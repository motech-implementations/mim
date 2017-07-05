package org.motechproject.nms.flwUpdate.service.impl;

import org.apache.commons.lang.StringUtils;
import org.joda.time.LocalDate;
import org.motechproject.nms.csv.exception.CsvImportDataException;
import org.motechproject.nms.csv.utils.ConstraintViolationUtils;
import org.motechproject.nms.csv.utils.CsvImporterBuilder;
import org.motechproject.nms.csv.utils.CsvMapImporter;
import org.motechproject.nms.csv.utils.GetLong;
import org.motechproject.nms.csv.utils.GetString;
import org.motechproject.nms.csv.utils.GetLocalDate;

import org.motechproject.nms.flw.domain.FrontLineWorker;
import org.motechproject.nms.flw.domain.ContactNumberAudit;
import org.motechproject.nms.flw.domain.FrontLineWorkerStatus;
import org.motechproject.nms.flw.domain.FlwError;
import org.motechproject.nms.flw.domain.FlwErrorReason;
import org.motechproject.nms.flw.exception.FlwExistingRecordException;
import org.motechproject.nms.flw.exception.FlwImportException;
import org.motechproject.nms.flw.repository.ContactNumberAuditDataService;
import org.motechproject.nms.flw.repository.FlwErrorDataService;
import org.motechproject.nms.flw.service.FrontLineWorkerService;
import org.motechproject.nms.flw.utils.FlwConstants;
import org.motechproject.nms.flw.utils.FlwMapper;
import org.motechproject.nms.flwUpdate.service.FrontLineWorkerImportService;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;
import org.motechproject.nms.mobileacademy.service.MobileAcademyService;
import org.motechproject.nms.props.service.LogHelper;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.exception.InvalidLocationException;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.nms.region.service.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.prefs.CsvPreference;

import javax.jdo.JDODataStoreException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.motechproject.nms.flw.utils.FlwMapper.createFlw;
import static org.motechproject.nms.flw.utils.FlwMapper.createRchFlw;
import static org.motechproject.nms.flw.utils.FlwMapper.updateFlw;

@Service("frontLineWorkerImportService")
public class FrontLineWorkerImportServiceImpl implements FrontLineWorkerImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrontLineWorkerImportServiceImpl.class);
    private FrontLineWorkerService frontLineWorkerService;
    private StateDataService stateDataService;
    private LocationService locationService;
    private FlwErrorDataService flwErrorDataService;
    private MobileAcademyService mobileAcademyService;
    private ContactNumberAuditDataService contactNumberAuditDataService;

    /*
        Expected file format:
        * any number of empty lines
        * first non blank line to contain state name in the following format:  State Name : ACTUAL STATE_ID NAME
        * any number of additional header lines
        * one empty line
        * CSV data (tab-separated)
     */
    // CHECKSTYLE:OFF
    @Override
    @Transactional
    public void importData(Reader reader, SubscriptionOrigin importOrigin) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(reader);

        State state = importHeader(bufferedReader);
        CsvMapImporter csvImporter;
        String designation;

        if (importOrigin.equals(SubscriptionOrigin.MCTS_IMPORT)) {
            csvImporter = new CsvImporterBuilder()
                    .setProcessorMapping(getMctsProcessorMapping())
                    .setPreferences(CsvPreference.TAB_PREFERENCE)
                    .createAndOpen(bufferedReader);
            try {
                Map<String, Object> record;
                while (null != (record = csvImporter.read())) {
                    designation = (String) record.get(FlwConstants.TYPE);
                    designation = (designation != null) ? designation.trim() : designation;
                    if (FlwConstants.ASHA_TYPE.equalsIgnoreCase(designation)) {
                        importMctsFrontLineWorker(record, state);
                    }
                }
            } catch (ConstraintViolationException e) {
                throw new CsvImportDataException(createErrorMessage(e.getConstraintViolations(), csvImporter.getRowNumber()), e);
            } catch (InvalidLocationException | FlwImportException | JDODataStoreException | FlwExistingRecordException e) {
                throw new CsvImportDataException(createErrorMessage(e.getMessage(), csvImporter.getRowNumber()), e);
            }
        } else {
            csvImporter = new CsvImporterBuilder()
                    .setProcessorMapping(getRchProcessorMapping())
                    .setPreferences(CsvPreference.TAB_PREFERENCE)
                    .createAndOpen(bufferedReader);
            try {
                Map<String, Object> record;
                while (null != (record = csvImporter.read())) {
                    designation = (String) record.get(FlwConstants.GF_TYPE);
                    designation = (designation != null) ? designation.trim() : designation;
                    if (FlwConstants.ASHA_TYPE.equalsIgnoreCase(designation)) {
                        importRchFrontLineWorker(record, state);
                    }
                }
            } catch (ConstraintViolationException e) {
                throw new CsvImportDataException(createErrorMessage(e.getConstraintViolations(), csvImporter.getRowNumber()), e);
            } catch (InvalidLocationException | FlwImportException | JDODataStoreException | FlwExistingRecordException e) {
                throw new CsvImportDataException(createErrorMessage(e.getMessage(), csvImporter.getRowNumber()), e);
            }
        }
    }

    // CHECKSTYLE:ON
    @Override
    @Transactional
    public void importMctsFrontLineWorker(Map<String, Object> record, State state) throws InvalidLocationException, FlwExistingRecordException {
        FrontLineWorker flw = flwFromRecord(record, state);

        record.put(FlwConstants.STATE_ID, state.getCode());
        Map<String, Object> location = locationService.getLocations(record);

        if (flw == null) {
            FrontLineWorker frontLineWorker = createFlw(record, location);
            if (frontLineWorker != null) {
                frontLineWorkerService.add(frontLineWorker);
            }
        } else {
            LocalDate mctsUpdatedDateNic = (LocalDate) record.get(FlwConstants.UPDATED_ON);
            //It updated_date_nic from mcts is not null,then it's not a new record. Compare it with the record from database and update
            if (mctsUpdatedDateNic != null && (flw.getUpdatedDateNic() == null || mctsUpdatedDateNic.isAfter(flw.getUpdatedDateNic()) || mctsUpdatedDateNic.isEqual(flw.getUpdatedDateNic()))) {
                Long oldMsisdn = flw.getContactNumber();
                FrontLineWorker flwInstance = updateFlw(flw, record, location, SubscriptionOrigin.MCTS_IMPORT);
                frontLineWorkerService.update(flwInstance);
                Long newMsisdn = (Long) record.get(FlwConstants.CONTACT_NO);
                if (!oldMsisdn.equals(newMsisdn)) {
                    mobileAcademyService.updateMsisdn(flwInstance.getId(), oldMsisdn, newMsisdn);
                    ContactNumberAudit contactNumberAudit = new ContactNumberAudit(flwInstance.getId());
                    contactNumberAudit.setOldCallingNumber(oldMsisdn);
                    contactNumberAudit.setNewCallingNumber(newMsisdn);
                    contactNumberAudit.setImportDate(LocalDate.now());
                    contactNumberAuditDataService.create(contactNumberAudit);
                }
            } else {
                throw new FlwExistingRecordException("Updated record exists in the database");
            }
        }
    }

    @Override
    @Transactional
    public void importRchFrontLineWorker(Map<String, Object> record, State state) throws InvalidLocationException, FlwExistingRecordException {
        String flwId = (String) record.get(FlwConstants.GF_ID);
        Long msisdn = (Long) record.get(FlwConstants.MOBILE_NO);

        record.put(FlwConstants.STATE_ID, state.getCode());
        Map<String, Object> location = locationService.getLocations(record);

        FrontLineWorker flw = frontLineWorkerService.getByMctsFlwIdAndState(flwId, state);
        if (flw != null) {
            FrontLineWorker flw2 = frontLineWorkerService.getByContactNumber(msisdn);
            if (flw2 == null || flw2.getStatus().equals(FrontLineWorkerStatus.ACTIVE)) {
                // update msisdn of existing asha worker
                FrontLineWorker flwInstance = updateFlw(flw, record, location, SubscriptionOrigin.RCH_IMPORT);
                frontLineWorkerService.update(flwInstance);
            } else {
                //we got here because an FLW exists with active job status and the same msisdn
                //check if both these records are the same or not
                if (flw.equals(flw2)) {
                    FrontLineWorker flwInstance = updateFlw(flw, record, location, SubscriptionOrigin.RCH_IMPORT);
                    frontLineWorkerService.update(flwInstance);
                } else {
                    LOGGER.debug("New flw but phone number(update) already in use");
                    flwErrorDataService.create(new FlwError(flwId, (long) record.get(FlwConstants.STATE_ID), (long) record.get(FlwConstants.DISTRICT_ID), FlwErrorReason.PHONE_NUMBER_IN_USE));
                    throw new FlwExistingRecordException("Msisdn already in use.");
                }
            }
        } else {
            FrontLineWorker frontLineWorker = frontLineWorkerService.getByContactNumber(msisdn);
            if (frontLineWorker != null && frontLineWorker.getStatus().equals(FrontLineWorkerStatus.ACTIVE)) {
                // check if anonymous FLW
                if (frontLineWorker.getMctsFlwId() == null) {
                    FrontLineWorker flwInstance = updateFlw(frontLineWorker, record, location, SubscriptionOrigin.RCH_IMPORT);
                    frontLineWorkerService.update(flwInstance);
                } else {
                    // reject the record
                    LOGGER.debug("Existing FLW with provided msisdn");
                    flwErrorDataService.create(new FlwError(flwId, (long) record.get(FlwConstants.STATE_ID), (long) record.get(FlwConstants.DISTRICT_ID), FlwErrorReason.PHONE_NUMBER_IN_USE));
                    throw new FlwExistingRecordException("Msisdn already in use.");
                }
            } else {
                // create new FLW record with provided flwId and msisdn
                FrontLineWorker newFlw = createRchFlw(record, location);
                if (newFlw != null) {
                    frontLineWorkerService.add(newFlw);
                }
            }
        }
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    public boolean createUpdate(Map<String, Object> flw) { //NOPMD NcssMethodCount

        long stateId = (long) flw.get(FlwConstants.STATE_ID);
        long districtId = (long) flw.get(FlwConstants.DISTRICT_ID);
        String mctsFlwId = flw.get(FlwConstants.ID).toString();
        long contactNumber = (long) flw.get(FlwConstants.CONTACT_NO);

        State state = locationService.getState(stateId);
        if (state == null) {
            flwErrorDataService.create(new FlwError(mctsFlwId, stateId, districtId, FlwErrorReason.INVALID_LOCATION_STATE));
            return false;
        }
        District district = locationService.getDistrict(stateId, districtId);
        if (district == null) {
            flwErrorDataService.create(new FlwError(mctsFlwId, stateId, districtId, FlwErrorReason.INVALID_LOCATION_DISTRICT));
            return false;
        }

        FrontLineWorker existingFlwByNumber = frontLineWorkerService.getByContactNumber(contactNumber);
        FrontLineWorker existingFlwByMctsFlwId = frontLineWorkerService.getByMctsFlwIdAndState(mctsFlwId, state);
        Map<String, Object> location = new HashMap<>();
        try {
            location = locationService.getLocations(flw, false);

            if (existingFlwByMctsFlwId != null && existingFlwByNumber != null) {

                if (existingFlwByMctsFlwId.getMctsFlwId().equalsIgnoreCase(existingFlwByNumber.getMctsFlwId()) &&
                        existingFlwByMctsFlwId.getState().equals(existingFlwByNumber.getState())) {
                    // we are trying to update the same existing flw. set fields and update
                    LOGGER.debug("Updating existing user with same phone number");
                    frontLineWorkerService.update(FlwMapper.updateFlw(existingFlwByMctsFlwId, flw, location, SubscriptionOrigin.MCTS_IMPORT));
                    return true;
                } else {
                    // we are trying to update 2 different users and/or phone number used by someone else
                    LOGGER.debug("Existing flw but phone number(update) already in use");
                    flwErrorDataService.create(new FlwError(mctsFlwId, stateId, districtId, FlwErrorReason.PHONE_NUMBER_IN_USE));
                    return false;
                }
            } else if (existingFlwByMctsFlwId != null && existingFlwByNumber == null) {
                // trying to update the phone number of the person. possible migration scenario
                // making design decision that flw will lose all progress when phone number is changed. Usage and tracking is not
                // worth the effort & we don't really know that its the same flw
                LOGGER.debug("Updating phone number for flw");
                long existingContactNumber = existingFlwByMctsFlwId.getContactNumber();
                FrontLineWorker flwInstance = FlwMapper.updateFlw(existingFlwByMctsFlwId, flw, location, SubscriptionOrigin.MCTS_IMPORT);
                updateFlwMaMsisdn(flwInstance, existingContactNumber, contactNumber);
                return true;
            } else if (existingFlwByMctsFlwId == null && existingFlwByNumber != null) {

                if (existingFlwByNumber.getMctsFlwId() == null) {
                    // we just got data from mcts for a previous anonymous user that subscribed by phone number
                    // merging those records
                    LOGGER.debug("Merging mcts data with previously anonymous user");
                    frontLineWorkerService.update(FlwMapper.updateFlw(existingFlwByNumber, flw, location, SubscriptionOrigin.MCTS_IMPORT));
                    return true;
                } else {
                    // phone number used by someone else.
                    LOGGER.debug("New flw but phone number(update) already in use");
                    flwErrorDataService.create(new FlwError(mctsFlwId, stateId, districtId, FlwErrorReason.PHONE_NUMBER_IN_USE));
                    return false;
                }

            } else { // existingFlwByMctsFlwId & existingFlwByNumber are null)
                // new user. set fields and add
                LOGGER.debug("Adding new flw user");
                FrontLineWorker frontLineWorker = FlwMapper.createFlw(flw, location);
                if (frontLineWorker != null) {
                    frontLineWorkerService.add(frontLineWorker);
                    return true;
                } else {
                    LOGGER.error("GF Status is INACTIVE. So cannot create record.");
                    return false;
                }
            }

        } catch (InvalidLocationException ile) {
            LOGGER.debug(ile.toString());
            return false;
        }
    }

    private void updateFlwMaMsisdn(FrontLineWorker flwInstance, Long existingMsisdn, Long newMsisdn) {
        frontLineWorkerService.update(flwInstance);
        mobileAcademyService.updateMsisdn(flwInstance.getId(), existingMsisdn, newMsisdn);
    }

    private State importHeader(BufferedReader bufferedReader) throws IOException {
        String line = readLineWhileBlank(bufferedReader);
        // expect state name in the first line
        if (line.matches("^State Name : .*$")) {
            String stateName = line.substring(line.indexOf(':') + 1).trim();
            State state = stateDataService.findByName(stateName);
            verify(null != state, "State does not exists");
            readLineWhileNotBlank(bufferedReader);
            return state;
        } else {
            throw new IllegalArgumentException("Invalid file format");
        }
    }

    private FrontLineWorker flwFromRecord(Map<String, Object> record, State state) {
        FrontLineWorker flw = null;

        String mctsFlwId = (String) record.get(FlwConstants.ID);
        Long msisdn = (Long) record.get(FlwConstants.CONTACT_NO);

        if (mctsFlwId != null) {
            flw = frontLineWorkerService.getByMctsFlwIdAndState(mctsFlwId, state);
        }

        if (flw == null && msisdn != null) {
            flw = frontLineWorkerService.getByContactNumber(msisdn);

            // If we loaded the flw by msisdn but the flw we found has a different mcts id
            // then the data needs to be hand corrected since we don't know if the msisdn has changed or
            // if the mcts id has changed.
            if (flw != null && mctsFlwId != null && flw.getMctsFlwId() != null && !mctsFlwId.equals(flw.getMctsFlwId())) {
                throw new CsvImportDataException(String.format("Existing FLW with same MSISDN (%s) but " +
                                        "different MCTS ID (%s != %s)", LogHelper.obscure(msisdn), mctsFlwId, flw.getMctsFlwId()));
            }
        }

        return flw;
    }

    private String readLineWhileBlank(BufferedReader bufferedReader) throws IOException {
        String line;
        do {
            line = bufferedReader.readLine();
        } while (null != line && StringUtils.isBlank(line));
        return line;
    }

    private String readLineWhileNotBlank(BufferedReader bufferedReader) throws IOException {
        String line;
        do {
            line = bufferedReader.readLine();
        } while (null != line && StringUtils.isNotBlank(line));
        return line;
    }

    private void getMapping(Map<String, CellProcessor> mapping) {
        mapping.put(FlwConstants.STATE_ID, new Optional(new GetLong()));

        mapping.put(FlwConstants.DISTRICT_ID, new Optional(new GetLong()));
        mapping.put(FlwConstants.DISTRICT_NAME, new Optional(new GetString()));

        mapping.put(FlwConstants.TALUKA_ID, new Optional(new GetString()));
        mapping.put(FlwConstants.TALUKA_NAME, new Optional(new GetString()));

        mapping.put(FlwConstants.HEALTH_BLOCK_ID, new Optional(new GetLong()));
        mapping.put(FlwConstants.HEALTH_BLOCK_NAME, new Optional(new GetString()));

        mapping.put(FlwConstants.PHC_ID, new Optional(new GetLong()));
        mapping.put(FlwConstants.PHC_NAME, new Optional(new GetString()));

        mapping.put(FlwConstants.SUB_CENTRE_ID, new Optional(new GetLong()));
        mapping.put(FlwConstants.SUB_CENTRE_NAME, new Optional(new GetString()));

        mapping.put(FlwConstants.CENSUS_VILLAGE_ID, new Optional(new GetLong()));
        mapping.put(FlwConstants.NON_CENSUS_VILLAGE_ID, new Optional(new GetLong()));
        mapping.put(FlwConstants.VILLAGE_NAME, new Optional(new GetString()));
    }

    private Map<String, CellProcessor> getMctsProcessorMapping() {
        Map<String, CellProcessor> mapping = new HashMap<>();
        mapping.put(FlwConstants.ID, new GetString());
        mapping.put(FlwConstants.CONTACT_NO, new GetLong());
        mapping.put(FlwConstants.NAME, new GetString());
        getMapping(mapping);
        mapping.put(FlwConstants.TYPE, new Optional(new GetString()));
        mapping.put(FlwConstants.GF_STATUS, new Optional(new GetString()));
        mapping.put(FlwConstants.UPDATED_ON, new Optional(new GetLocalDate()));

        return mapping;
    }

    private Map<String, CellProcessor> getRchProcessorMapping() {
        Map<String, CellProcessor> mapping = new HashMap<>();
        mapping.put(FlwConstants.GF_ID, new GetString());
        mapping.put(FlwConstants.MOBILE_NO, new GetLong());
        mapping.put(FlwConstants.GF_NAME, new GetString());
        getMapping(mapping);
        mapping.put(FlwConstants.GF_TYPE, new Optional(new GetString()));
        mapping.put(FlwConstants.EXEC_DATE, new Optional(new GetLocalDate()));
        mapping.put(FlwConstants.GF_STATUS, new Optional(new GetString()));

        return mapping;
    }

    private String createErrorMessage(String message, int rowNumber) {
        return String.format("CSV instance error [row: %d]: %s", rowNumber, message);
    }

    private String createErrorMessage(Set<ConstraintViolation<?>> violations, int rowNumber) {
        return String.format("CSV instance error [row: %d]: validation failed for instance of type %s, violations: %s",
                rowNumber, FrontLineWorker.class.getName(), ConstraintViolationUtils.toString(violations));
    }

    private void verify(boolean condition, String message) {
        if (!condition) {
            throw new CsvImportDataException(message);
        }
    }

    @Autowired
    public void setFrontLineWorkerService(FrontLineWorkerService frontLineWorkerService) {
        this.frontLineWorkerService = frontLineWorkerService;
    }

    @Autowired
    public void setStateDataService(StateDataService stateDataService) {
        this.stateDataService = stateDataService;
    }

    @Autowired
    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }

    @Autowired
    public void setFlwErrorDataService(FlwErrorDataService flwErrorDataService) {
        this.flwErrorDataService = flwErrorDataService;
    }

    @Autowired
    public void setMobileAcademyService(MobileAcademyService mobileAcademyService) {
        this.mobileAcademyService = mobileAcademyService;
    }

    @Autowired
    public void setContactNumberAuditDataService(ContactNumberAuditDataService contactNumberAuditDataService) {
        this.contactNumberAuditDataService = contactNumberAuditDataService;
    }

}
