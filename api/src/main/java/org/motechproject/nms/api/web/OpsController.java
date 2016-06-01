package org.motechproject.nms.api.web;

import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.nms.imi.service.CdrFileService;
import org.motechproject.nms.kilkari.repository.SubscriptionDataService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.kilkari.utils.KilkariConstants;
import org.motechproject.nms.mcts.service.MctsWsImportService;
import org.motechproject.nms.props.service.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Controller to expose methods for OPS personnel
 */
@RequestMapping("/ops")
@Controller
public class OpsController extends BaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpsController.class);
    private SubscriptionDataService subscriptionDataService;
    private SubscriptionService subscriptionService;
    private CdrFileService cdrFileService;
    private MctsWsImportService mctsWsImportService;
    private EventRelay eventRelay;


    @Autowired
    public OpsController(SubscriptionDataService subscriptionDataService, SubscriptionService subscriptionService,
                         CdrFileService cdrFileService, MctsWsImportService mctsWsImportService, EventRelay eventRelay) {
        this.subscriptionDataService = subscriptionDataService;
        this.subscriptionService = subscriptionService;
        this.cdrFileService = cdrFileService;
        this.mctsWsImportService = mctsWsImportService;
        this.eventRelay = eventRelay;
    }

    /**
     * Provided for OPS as a crutch to be able to empty all MDS cache directly after modifying the database by hand
     */
    @RequestMapping("/evictAllCache")
    @ResponseStatus(HttpStatus.OK)
    public void evictAllCache() {
        LOGGER.info("/evictAllCache()");
        subscriptionDataService.evictAllCache();
    }

    @RequestMapping("/cleanSubscriptions")
    @ResponseStatus(HttpStatus.OK)
    public void cleanSubscriptions() {

        LOGGER.info("/cleanSubscriptions()");
        subscriptionService.completePastDueSubscriptions();
    }

    @RequestMapping("/cleanCallRecords")
    @ResponseStatus(HttpStatus.OK)
    public void clearCallRecords() {

        LOGGER.info("/cleanCdr()");
        cdrFileService.cleanOldCallRecords();
    }

    @RequestMapping("/startMctsSync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void startMctsSync() {

        LOGGER.info("/startMctsSync");
        mctsWsImportService.startMctsImport();
    }

    @RequestMapping("/upkeep")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void startUpkeep() {
        LOGGER.info("/upkeep");
        eventRelay.sendEventMessage(new MotechEvent(KilkariConstants.SUBSCRIPTION_UPKEEP_SUBJECT));
    }

    @RequestMapping(value = "/addFlw",
            method = RequestMethod.POST,
            headers = { "Content-type=application/json" })
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public void createUpdateFlw(@RequestBody AddFlwRequest addFlwRequest) {
        // TODO: obscure phone number?
        log("REQUEST: /ops/addFlw (POST)", LogHelper.nullOrString(addFlwRequest));
        StringBuilder failureReasons = new StringBuilder();
        validateField10Digits(failureReasons, "contactNumber", addFlwRequest.getContactNumber());
        validateFieldPositiveLong(failureReasons, "contactNumber", addFlwRequest.getContactNumber());
        validateFieldPresent(failureReasons, "mctsFlwId", addFlwRequest.getMctsFlwId());
        validateFieldPresent(failureReasons, "stateId", addFlwRequest.getStateId());
        validateFieldPresent(failureReasons, "districtId", addFlwRequest.getDistrictId());
        validateFieldString(failureReasons, "name", addFlwRequest.getName());

        if (failureReasons.length() > 0) {
            throw new IllegalArgumentException(failureReasons.toString());
        }

        // TODO: Hook up existing flw create/update here
    }
}
