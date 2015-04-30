package org.motechproject.nms.outbounddialer.service.impl;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.motechproject.alerts.contract.AlertService;
import org.motechproject.alerts.domain.AlertStatus;
import org.motechproject.alerts.domain.AlertType;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.repository.SubscriptionDataService;
import org.motechproject.nms.outbounddialer.domain.CallRetry;
import org.motechproject.nms.outbounddialer.domain.DayOfTheWeek;
import org.motechproject.nms.outbounddialer.domain.FileProcessedStatus;
import org.motechproject.nms.outbounddialer.repository.CallRetryDataService;
import org.motechproject.nms.outbounddialer.service.TargetFileService;
import org.motechproject.nms.outbounddialer.web.contract.FileProcessedStatusRequest;
import org.motechproject.scheduler.contract.RepeatingSchedulableJob;
import org.motechproject.scheduler.service.MotechSchedulerService;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@Service("targetFileService")
public class TargetFileServiceImpl implements TargetFileService {
    private static final String TARGET_FILE_TIME = "outbound-dialer.target_file_time";
    private static final String MAX_QUERY_BLOCK = "outbound-dialer.max_query_block";
    private static final String TARGET_FILE_MS_INTERVAL = "outbound-dialer.target_file_ms_interval";
    private static final String TARGET_FILE_DIRECTORY = "outbound-dialer.target_file_directory";
    private static final String GENERATE_TARGET_FILE_EVENT = "nms.obd.generate_target_file";

    private SettingsFacade settingsFacade;
    private MotechSchedulerService schedulerService;
    private AlertService alertService;
    private SubscriptionDataService subscriptionDataService;
    private CallRetryDataService callRetryDataService;

    private static final Logger LOGGER = LoggerFactory.getLogger(TargetFileServiceImpl.class);


    private void scheduleTargetFileGeneration() {
        //Calculate today's fire time
        DateTimeFormatter fmt = DateTimeFormat.forPattern("H:m");
        String timeProp = settingsFacade.getProperty(TARGET_FILE_TIME);
        DateTime time = fmt.parseDateTime(timeProp);
        DateTime today = DateTime.now()
                .withHourOfDay(time.getHourOfDay())
                .withMinuteOfHour(time.getMinuteOfHour())
                .withSecondOfMinute(0)
                .withMillisOfSecond(0);

        //Millisecond interval between events
        String intervalProp = settingsFacade.getProperty(TARGET_FILE_MS_INTERVAL);
        Long msInterval = Long.parseLong(intervalProp);

        LOGGER.debug(String.format("The %s message will be sent every %sms starting %s",
                GENERATE_TARGET_FILE_EVENT, msInterval.toString(), today.toString()));

        //Schedule repeating job
        MotechEvent event = new MotechEvent(GENERATE_TARGET_FILE_EVENT);
        RepeatingSchedulableJob job = new RepeatingSchedulableJob(
                event,          //MOTECH event
                today.toDate(), //startTime
                null,           //endTime, null means no end time
                null,           //repeatCount, null means infinity
                msInterval,     //repeatIntervalInMilliseconds
                true);          //ignorePastFiresAtStart
        schedulerService.safeScheduleRepeatingJob(job);
    }


    @Autowired
    public TargetFileServiceImpl(@Qualifier("outboundDialerSettings") SettingsFacade settingsFacade,
                                 MotechSchedulerService schedulerService, AlertService alertService,
                                 SubscriptionDataService subscriptionDataService,
                                 CallRetryDataService callRetryDataService) {
        this.schedulerService = schedulerService;
        this.settingsFacade = settingsFacade;
        this.alertService = alertService;
        this.subscriptionDataService = subscriptionDataService;
        this.callRetryDataService = callRetryDataService;

        scheduleTargetFileGeneration();
    }


    private String targetFileName() {
        DateTime today = DateTime.now();
        return String.format("OBD_%04d%02d%02d%02d%02d%02d.csv",
                today.getYear(),
                today.getMonthOfYear(),
                today.getDayOfMonth(),
                today.getHourOfDay(),
                today.getMinuteOfHour(),
                today.getSecondOfMinute());
    }


    private File createTargetFileDirectory() {
        File userHome = new File(System.getProperty("user.home"));
        File targetFileDirectory = new File(userHome, settingsFacade.getProperty(TARGET_FILE_DIRECTORY));

        if (targetFileDirectory.exists()) {
            LOGGER.info("targetFile directory exists: {}", targetFileDirectory);
        } else {
            LOGGER.info("creating targetFile directory: {}", targetFileDirectory);
            if (!targetFileDirectory.mkdirs()) {
                String error = String.format("Unable to create targetFileDirectory %s: mkdirs() failed",
                        targetFileDirectory);
                LOGGER.error(error);
                alertService.create(targetFileDirectory.toString(), "targetFileDirectory", error, AlertType.CRITICAL,
                        AlertStatus.NEW, 0, null);
                throw new IllegalStateException(error);
            }
        }
        return targetFileDirectory;
    }


    private PrintWriter createTargetFile(File targetDirectory) {
        String targetFileName = targetFileName();
        try {
            File targetFile = new File(targetDirectory, targetFileName);
            LOGGER.info("Creating targetFile: {}", targetFile);
            return new PrintWriter(targetFile, "UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Unable to create targetFile %s in %s: %s",
                    targetFileName, targetDirectory, e.getMessage()));
        }
    }


    public void generateTargetFile() {
        File targetFileDirectory = createTargetFileDirectory();
        PrintWriter writer = createTargetFile(targetFileDirectory);

        //figure out which day to work with
        final DayOfTheWeek today = DayOfTheWeek.today();


        long numRecord = 0;
        int maxQueryBlock = Integer.parseInt(settingsFacade.getProperty(MAX_QUERY_BLOCK));

        //Fresh calls
        int page = 1;
        int numBlockRecord = 0;
        do {
            //todo: replace retrieveAll with findByStatus when available
            List<Subscription> subscriptions = subscriptionDataService.retrieveAll(
                    new QueryParams(page, maxQueryBlock));
            numBlockRecord = subscriptions.size();

            for (Subscription subscription : subscriptions) {
                writer.print(subscription.getSubscriptionId());
                writer.print(",");
                writer.print(subscription.getSubscriber().getCallingNumber());
                writer.print(",");
                writer.println(subscription.getLanguage().getCode());
                //todo...
            }

            page++;
            numRecord += numBlockRecord;

        } while (numBlockRecord > 0);

        //Retry calls
        page = 1;
        numBlockRecord = 0;
        do {
            List<CallRetry> callRetries = callRetryDataService.findByDayOfTheWeek(today,
                    new QueryParams(page, maxQueryBlock));
            numBlockRecord = callRetries.size();

            for (CallRetry callRetry : callRetries) {
                writer.print(callRetry.getSubscriptionId());
                writer.print(",");
                writer.print(callRetry.getMsisdn());
                writer.print(",");
                writer.println(callRetry.getLanguageLocationCode());
                //todo...
            }

            page++;
            numRecord += numBlockRecord;

        } while (numBlockRecord > 0);

        LOGGER.info("Created targetFile with {} beneficiar{}", numRecord, numRecord == 1 ? "y" : "ies");

        //todo...
        //notify the IVR system the file is ready

    }


    @MotechListener(subjects = { GENERATE_TARGET_FILE_EVENT })
    public void generateTargetFile(MotechEvent event) {
        LOGGER.debug(event.toString());
        generateTargetFile();
    }


    @Override
    public void handleFileProcessedStatusNotification(FileProcessedStatusRequest request) {
        if (request.getFileProcessedStatus() == FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY) {
            LOGGER.info(request.toString());
            //We're happy.
            //todo:...
        } else {
            LOGGER.error(request.toString());
            alertService.create(
                    request.getFileName(),
                    "targetFileName",
                    "Target File Processing Error",
                    AlertType.CRITICAL,
                    AlertStatus.NEW,
                    0,
                    null);
        }
    }
}
