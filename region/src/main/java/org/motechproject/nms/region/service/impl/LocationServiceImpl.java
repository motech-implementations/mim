package org.motechproject.nms.region.service.impl;

import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.metrics.service.Timer;
import org.motechproject.nms.csv.exception.CsvImportDataException;
import org.motechproject.nms.csv.utils.CsvMapImporter;
import org.motechproject.nms.csv.utils.ConstraintViolationUtils;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.Taluka;
import org.motechproject.nms.region.domain.Village;
import org.motechproject.nms.region.domain.HealthBlock;
import org.motechproject.nms.region.domain.HealthFacility;
import org.motechproject.nms.region.domain.HealthSubFacility;
import org.motechproject.nms.region.domain.LocationFinder;
import org.motechproject.nms.region.exception.InvalidLocationException;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.nms.region.repository.DistrictDataService;
import org.motechproject.nms.region.repository.TalukaDataService;
import org.motechproject.nms.region.repository.VillageDataService;
import org.motechproject.nms.region.repository.HealthBlockDataService;
import org.motechproject.nms.region.repository.HealthFacilityDataService;
import org.motechproject.nms.region.repository.HealthSubFacilityDataService;
import org.motechproject.nms.region.service.DistrictService;
import org.motechproject.nms.region.service.HealthBlockService;
import org.motechproject.nms.region.service.HealthFacilityService;
import org.motechproject.nms.region.service.HealthSubFacilityService;
import org.motechproject.nms.region.service.LocationService;
import org.motechproject.nms.region.service.TalukaService;
import org.motechproject.nms.region.service.VillageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.jdo.Query;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * Location service impl to get location objects
 */
@Service("locationService")
public class LocationServiceImpl implements LocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocationServiceImpl.class);

    private static final String INVALID = "<%s - %s : Invalid location>";
    private static final String STATE_ID = "StateID";
    private static final String DISTRICT_ID = "District_ID";
    private static final String DISTRICT_NAME = "District_Name";
    private static final String TALUKA_ID = "Taluka_ID";
    private static final String TALUKA_NAME = "Taluka_Name";
    private static final String HEALTHBLOCK_ID = "HealthBlock_ID";
    private static final String HEALTHBLOCK_NAME = "HealthBlock_Name";
    private static final String PHC_ID = "PHC_ID";
    private static final String PHC_NAME = "PHC_Name";
    private static final String SUBCENTRE_ID = "SubCentre_ID";
    private static final String SUBCENTRE_NAME = "SubCentre_Name";
    private static final String VILLAGE_ID = "Village_ID";
    private static final String VILLAGE_NAME = "Village_Name";
    private static final String NON_CENSUS_VILLAGE = "SVID";

    private static final String DATE_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss";

    private StateDataService stateDataService;

    private DistrictService districtService;

    private TalukaService talukaService;

    private VillageService villageService;

    private HealthBlockService healthBlockService;

    private HealthFacilityService healthFacilityService;

    private HealthSubFacilityService healthSubFacilityService;

    private DistrictDataService districtDataService;

    private TalukaDataService talukaDataService;

    private VillageDataService villageDataService;

    private HealthBlockDataService healthBlockDataService;

    private HealthFacilityDataService healthFacilityDataService;

    private HealthSubFacilityDataService healthSubFacilityDataService;

    @Autowired
    public LocationServiceImpl(StateDataService stateDataService, DistrictService districtService,
                               TalukaService talukaService, VillageService villageService,
                               HealthBlockService healthBlockService, HealthFacilityService healthFacilityService,
                               HealthSubFacilityService healthSubFacilityService, DistrictDataService districtDataService, TalukaDataService talukaDataService, VillageDataService villageDataService, HealthBlockDataService healthBlockDataService, HealthFacilityDataService healthFacilityDataService, HealthSubFacilityDataService healthSubFacilityDataService) {
        this.stateDataService = stateDataService;
        this.districtService = districtService;
        this.talukaService = talukaService;
        this.villageService = villageService;
        this.healthBlockService = healthBlockService;
        this.healthFacilityService = healthFacilityService;
        this.healthSubFacilityService = healthSubFacilityService;
        this.districtDataService = districtDataService;
        this.talukaDataService = talukaDataService;
        this.villageDataService = villageDataService;
        this.healthBlockDataService = healthBlockDataService;
        this.healthFacilityDataService = healthFacilityDataService;
        this.healthSubFacilityDataService = healthSubFacilityDataService;
    }


    private boolean isValidID(final Map<String, Object> map, final String key) {
        Object obj = map.get(key);
        if (obj == null || obj.toString().isEmpty() || "NULL".equalsIgnoreCase(obj.toString())) {
            return false;
        }

        if (obj.getClass().equals(Long.class)) {
            return (Long) obj > 0L;
        }

        return !"0".equals(obj);
    }

    public Map<String, Object> getLocations(Map<String, Object> map) throws InvalidLocationException {
       return getLocations(map, true);
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    @SuppressWarnings("PMD")
    public Map<String, Object> getLocations(Map<String, Object> map, boolean createIfNotExists) throws InvalidLocationException {

        Map<String, Object> locations = new HashMap<>();

        LOGGER.info("map {}", isValidID(map, STATE_ID));

        // set state
        if (!isValidID(map, STATE_ID)) {
            return locations;
        }
        State state = stateDataService.findByCode((Long) map.get(STATE_ID));
        LOGGER.info("state {}", state);
        if (state == null) { // we are here because stateId wasn't null but fetch returned no data
            throw new InvalidLocationException(String.format(INVALID, STATE_ID, map.get(STATE_ID)));
        }
        locations.put(STATE_ID, state);


        // set district
        if (!isValidID(map, DISTRICT_ID)) {
            return locations;
        }
        District district = districtService.findByStateAndCode(state, (Long) map.get(DISTRICT_ID));
        if (district == null) {
            throw new InvalidLocationException(String.format(INVALID, DISTRICT_ID, map.get(DISTRICT_ID)));
        }
        locations.put(DISTRICT_ID, district);


        // set and/or create taluka
        if (!isValidID(map, TALUKA_ID)) {
            return locations;
        }
        Taluka taluka = talukaService.findByDistrictAndCode(district, (String) map.get(TALUKA_ID));
        if (taluka == null && createIfNotExists) {
            taluka = new Taluka();
            taluka.setCode((String) map.get(TALUKA_ID));
            taluka.setName((String) map.get(TALUKA_NAME));
            taluka.setDistrict(district);
            district.getTalukas().add(taluka);
            LOGGER.debug(String.format("Created %s in %s with id %d", taluka, district, taluka.getId()));
        }
        locations.put(TALUKA_ID, taluka);


        // set and/or create village
        Long svid = map.get(NON_CENSUS_VILLAGE) == null ? 0 : (Long) map.get(NON_CENSUS_VILLAGE);
        Long vcode = map.get(VILLAGE_ID) == null ? 0 : (Long) map.get(VILLAGE_ID);
        if (vcode != 0 || svid != 0) {
            Village village = villageService.findByTalukaAndVcodeAndSvid(taluka, vcode, svid);
            if (village == null && createIfNotExists) {
                village = new Village();
                village.setSvid(svid);
                village.setVcode(vcode);
                village.setTaluka(taluka);
                village.setName((String) map.get(VILLAGE_NAME));
                taluka.getVillages().add(village);
                LOGGER.debug(String.format("Created %s in %s with id %d", village, taluka, village.getId()));
            }
            locations.put(VILLAGE_ID + NON_CENSUS_VILLAGE, village);
        }


        // set and/or create health block
        if (!isValidID(map, HEALTHBLOCK_ID)) {
            return locations;
        }
        HealthBlock healthBlock = healthBlockService.findByTalukaAndCode(taluka, (Long) map.get(HEALTHBLOCK_ID));
        if (healthBlock == null && createIfNotExists) {
            healthBlock = new HealthBlock();
            healthBlock.setTaluka(taluka);
            healthBlock.setCode((Long) map.get(HEALTHBLOCK_ID));
            healthBlock.setName((String) map.get(HEALTHBLOCK_NAME));
            taluka.getHealthBlocks().add(healthBlock);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthBlock, taluka, healthBlock.getId()));
        }
        locations.put(HEALTHBLOCK_ID, healthBlock);


        // set and/or create health facility
        if (!isValidID(map, PHC_ID)) {
            return locations;
        }
        HealthFacility healthFacility = healthFacilityService.findByHealthBlockAndCode(healthBlock, (Long) map.get(PHC_ID));
        if (healthFacility == null && createIfNotExists) {
            healthFacility = new HealthFacility();
            healthFacility.setHealthBlock(healthBlock);
            healthFacility.setCode((Long) map.get(PHC_ID));
            healthFacility.setName((String) map.get(PHC_NAME));
            healthBlock.getHealthFacilities().add(healthFacility);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthFacility, healthBlock, healthFacility.getId()));
        }
        locations.put(PHC_ID, healthFacility);


        // set and/or create health sub-facility
        if (!isValidID(map, SUBCENTRE_ID)) {
            return locations;
        }
        HealthSubFacility healthSubFacility = healthSubFacilityService.findByHealthFacilityAndCode(healthFacility, (Long) map.get(SUBCENTRE_ID));
        if (healthSubFacility == null && createIfNotExists) {
            healthSubFacility = new HealthSubFacility();
            healthSubFacility.setHealthFacility(healthFacility);
            healthSubFacility.setCode((Long) map.get(SUBCENTRE_ID));
            healthSubFacility.setName((String) map.get(SUBCENTRE_NAME));
            healthFacility.getHealthSubFacilities().add(healthSubFacility);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthSubFacility, healthFacility, healthSubFacility.getId()));
        }
        locations.put(SUBCENTRE_ID, healthSubFacility);

        return locations;
    }

    @Override
    public Taluka updateTaluka(Map<String, Object> flw, Boolean createIfNotExists) {
        State state = stateDataService.findByCode((Long) flw.get(STATE_ID));
        District district = districtService.findByStateAndCode(state, (Long) flw.get(DISTRICT_ID));
            // set and/or create taluka
        if (!isValidID(flw, TALUKA_ID)) {
            return null;
        }
        Taluka taluka = talukaService.findByDistrictAndCode(district, (String) flw.get(TALUKA_ID));
        if (taluka == null && createIfNotExists) {
            taluka = new Taluka();
            taluka.setCode((String) flw.get(TALUKA_ID));
            taluka.setName((String) flw.get(TALUKA_NAME));
            taluka.setDistrict(district);
            LOGGER.debug(String.format("taluka: %s", taluka.toString()));
            district.getTalukas().add(taluka);
            LOGGER.debug(String.format("Created %s in %s with id %d", taluka, district, taluka.getId()));
        }
        return taluka;
    }

    @Override
    public HealthBlock updateBlock(Map<String, Object> flw, Taluka taluka, Boolean createIfNotExists) {

        // set and/or create health block
        if (!isValidID(flw, HEALTHBLOCK_ID)) {
            return null;
        }
        HealthBlock healthBlock = healthBlockService.findByTalukaAndCode(taluka, (Long) flw.get(HEALTHBLOCK_ID));
        if (healthBlock == null && createIfNotExists) {
            healthBlock = new HealthBlock();
            healthBlock.setTaluka(taluka);
            healthBlock.setCode((Long) flw.get(HEALTHBLOCK_ID));
            healthBlock.setName((String) flw.get(HEALTHBLOCK_NAME));
            taluka.getHealthBlocks().add(healthBlock);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthBlock, taluka, healthBlock.getId()));
        }
        return healthBlock;
    }

    @Override
    public HealthFacility updateFacility(Map<String, Object> flw, HealthBlock healthBlock, Boolean createIfNotExists) {

        // set and/or create health facility
        if (!isValidID(flw, PHC_ID)) {
            return null;
        }
        HealthFacility healthFacility = healthFacilityService.findByHealthBlockAndCode(healthBlock, (Long) flw.get(PHC_ID));
        if (healthFacility == null && createIfNotExists) {
            healthFacility = new HealthFacility();
            healthFacility.setHealthBlock(healthBlock);
            healthFacility.setCode((Long) flw.get(PHC_ID));
            healthFacility.setName((String) flw.get(PHC_NAME));
            healthBlock.getHealthFacilities().add(healthFacility);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthFacility, healthBlock, healthFacility.getId()));
        }
        return healthFacility;
    }

    @Override
    public HealthSubFacility updateSubFacility(Map<String, Object> flw, HealthFacility healthFacility, Boolean createIfNotExists) {

        // set and/or create health sub-facility
        if (!isValidID(flw, SUBCENTRE_ID)) {
            return null;
        }
        HealthSubFacility healthSubFacility = healthSubFacilityService.findByHealthFacilityAndCode(healthFacility, (Long) flw.get(SUBCENTRE_ID));
        if (healthSubFacility == null && createIfNotExists) {
            healthSubFacility = new HealthSubFacility();
            healthSubFacility.setHealthFacility(healthFacility);
            healthSubFacility.setCode((Long) flw.get(SUBCENTRE_ID));
            healthSubFacility.setName((String) flw.get(SUBCENTRE_NAME));
            healthFacility.getHealthSubFacilities().add(healthSubFacility);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthSubFacility, healthFacility, healthSubFacility.getId()));
        }
        return healthSubFacility;
    }

    @Override
    public Village updateVillage(Map<String, Object> flw, Taluka taluka, Boolean createIfNotExists) {

        // set and/or create village
        Long svid = flw.get(NON_CENSUS_VILLAGE) == null ? 0 : (Long) flw.get(NON_CENSUS_VILLAGE);
        Long vcode = flw.get(VILLAGE_ID) == null ? 0 : (Long) flw.get(VILLAGE_ID);
         if (vcode != 0 || svid != 0) {
             Village village = villageService.findByTalukaAndVcodeAndSvid(taluka, vcode, svid);
             if (village == null && createIfNotExists) {
                 village = new Village();
                 village.setSvid(svid);
                 village.setVcode(vcode);
                 village.setTaluka(taluka);
                 village.setName((String) flw.get(VILLAGE_NAME));
                 taluka.getVillages().add(village);
                 LOGGER.debug(String.format("Created %s in %s with id %d", village, taluka, village.getId()));
             }
             return village;
         }
        return null;
    }


    @Override
    public State getState(Long stateId) {

        return stateDataService.findByCode(stateId);
    }

    @Override
    public District getDistrict(Long stateId, Long districtId) {

        State state = getState(stateId);

        if (state != null) {
            return districtService.findByStateAndCode(state, districtId);
        }

        return null;
    }

    @Override
    public Taluka getTaluka(Long stateId, Long districtId, String talukaId) {

        District district = getDistrict(stateId, districtId);

        if (district != null) {
            return talukaService.findByDistrictAndCode(district, talukaId);
        }

        return null;
    }

    @Override
    public Village getVillage(Long stateId, Long districtId, String talukaId, Long vCode, Long svid) {

        Taluka taluka = getTaluka(stateId, districtId, talukaId);

        if (taluka != null) {
            return villageService.findByTalukaAndVcodeAndSvid(taluka, vCode, svid);
        }

        return null;
    }

    @Override
    public Village getCensusVillage(Long stateId, Long districtId, String talukaId, Long vCode) {

        return getVillage(stateId, districtId, talukaId, vCode, 0L);
    }

    @Override
    public Village getNonCensusVillage(Long stateId, Long districtId, String talukaId, Long svid) {

        return getVillage(stateId, districtId, talukaId, 0L, svid);
    }

    @Override
    public HealthBlock getHealthBlock(Long stateId, Long districtId, String talukaId, Long healthBlockId) {

        Taluka taluka = getTaluka(stateId, districtId, talukaId);

        if (taluka != null) {

            return healthBlockService.findByTalukaAndCode(taluka, healthBlockId);
        }

        return null;
    }

    @Override
    public HealthFacility getHealthFacility(Long stateId, Long districtId, String talukaId, Long healthBlockId,
                                            Long healthFacilityId) {

        HealthBlock healthBlock = getHealthBlock(stateId, districtId, talukaId, healthBlockId);

        if (healthBlock != null) {

            return healthFacilityService.findByHealthBlockAndCode(healthBlock, healthFacilityId);
        }

        return null;
    }

    @Override
    public HealthSubFacility getHealthSubFacility(Long stateId, Long districtId, String talukaId,
                                                  Long healthBlockId, Long healthFacilityId,
                                                  Long healthSubFacilityId) {

        HealthFacility healthFacility = getHealthFacility(stateId, districtId, talukaId, healthBlockId,
                healthFacilityId);

        if (healthFacility != null) {

            return healthSubFacilityService.findByHealthFacilityAndCode(healthFacility, healthSubFacilityId);
        }

        return null;
    }

    @Override
    public void updateLocations(CsvMapImporter csvMapImporter, LocationFinder locationFinder) throws IOException {
        int count = 0;

        HashMap<String, State> stateHashMap = new HashMap<>();
        HashMap<String, District> districtHashMap = new HashMap<>();
        HashMap<String, Taluka> talukaHashMap = new HashMap<>();
        HashMap<String, Village> villageHashMap = new HashMap<>();
        HashMap<String, HealthBlock> healthBlockHashMap = new HashMap<>();
        HashMap<String, HealthFacility> healthFacilityHashMap = new HashMap<>();
        HashMap<String, HealthSubFacility> healthSubFacilityHashMap = new HashMap<>();
        try {
            Map<String, Object> record;

            while (null != (record = csvMapImporter.read())) {
                count++;
                String mapKey = record.get(STATE_ID).toString();
                if (isValidID(record, STATE_ID)) {
                    stateHashMap.put(mapKey, null);
                    mapKey += "_";
                    mapKey += record.get(DISTRICT_ID).toString();
                    if (isValidID(record, DISTRICT_ID)) {
                        districtHashMap.put(mapKey, null);

                        if (isValidID(record, TALUKA_ID)) {
                            Taluka taluka = new Taluka();
                            taluka.setCode((String) record.get(TALUKA_ID));
                            taluka.setName((String) record.get(TALUKA_NAME));
                            mapKey += "_";
                            mapKey += record.get(TALUKA_ID).toString();
                            talukaHashMap.put(mapKey, taluka);

                            Long svid = record.get(NON_CENSUS_VILLAGE) == null ? 0 : (Long) record.get(NON_CENSUS_VILLAGE);
                            Long vcode = record.get(VILLAGE_ID) == null ? 0 : (Long) record.get(VILLAGE_ID);
                            if (vcode != 0 || svid != 0) {
                                Village village = new Village();
                                village.setSvid((Long) record.get(NON_CENSUS_VILLAGE));
                                village.setVcode((Long) record.get(VILLAGE_ID));
                                village.setName((String) record.get(VILLAGE_NAME));
                                villageHashMap.put(mapKey + "_" + record.get(VILLAGE_ID).toString() + "_" +
                                        record.get(NON_CENSUS_VILLAGE).toString(), village);
                            }

                            if (isValidID(record, HEALTHBLOCK_ID)) {
                                HealthBlock healthBlock = new HealthBlock();
                                healthBlock.setCode((Long) record.get(HEALTHBLOCK_ID));
                                healthBlock.setName((String) record.get(HEALTHBLOCK_NAME));
                                mapKey += "_";
                                mapKey += record.get(HEALTHBLOCK_ID).toString();
                                healthBlockHashMap.put(mapKey, healthBlock);

                                if (isValidID(record, PHC_ID)) {
                                    HealthFacility healthFacility = new HealthFacility();
                                    healthFacility.setCode((Long) record.get(PHC_ID));
                                    healthFacility.setName((String) record.get(PHC_NAME));
                                    mapKey += "_";
                                    mapKey += record.get(PHC_ID).toString();
                                    healthFacilityHashMap.put(mapKey, healthFacility);

                                    if (isValidID(record, SUBCENTRE_ID)) {
                                        HealthSubFacility healthSubFacility = new HealthSubFacility();
                                        healthSubFacility.setCode((Long) record.get(SUBCENTRE_ID));
                                        healthSubFacility.setName((String) record.get(SUBCENTRE_NAME));
                                        mapKey += "_";
                                        mapKey += record.get(SUBCENTRE_ID).toString();
                                        healthSubFacilityHashMap.put(mapKey, healthSubFacility);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (ConstraintViolationException e) {
            throw new CsvImportDataException(String.format("Locations import error, constraints violated: %s",
                    ConstraintViolationUtils.toString(e.getConstraintViolations())), e);
        }
        if (!stateHashMap.isEmpty()) {
            fillStates(stateHashMap);
            locationFinder.setStateHashMap(stateHashMap);

            if (!districtHashMap.isEmpty()) {
                fillDistricts(districtHashMap, stateHashMap);
                locationFinder.setDistrictHashMap(districtHashMap);

                if (!talukaHashMap.isEmpty()) {
                    createUpdateTalukas(talukaHashMap, districtHashMap);
                    fillTalukas(talukaHashMap, districtHashMap);
                    locationFinder.setTalukaHashMap(talukaHashMap);

                    if (!villageHashMap.isEmpty()) {
                        createUpdateVillages(villageHashMap, talukaHashMap);
                        fillVillages(villageHashMap, talukaHashMap);
                        locationFinder.setVillageHashMap(villageHashMap);
                    }
                    if (!healthBlockHashMap.isEmpty()) {
                        createUpdateHealthBlocks(healthBlockHashMap, talukaHashMap);
                        fillHealthBlocks(healthBlockHashMap, talukaHashMap);
                        locationFinder.setHealthBlockHashMap(healthBlockHashMap);

                        if (!healthFacilityHashMap.isEmpty()) {
                            createUpdateHealthFacilities(healthFacilityHashMap, healthBlockHashMap);
                            fillHealthFacilities(healthFacilityHashMap, healthBlockHashMap);
                            locationFinder.setHealthFacilityHashMap(healthFacilityHashMap);

                            if (!healthSubFacilityHashMap.isEmpty()) {
                                createUpdateHealthSubFacilities(healthSubFacilityHashMap, healthFacilityHashMap);
                                fillHealthSubFacilities(healthSubFacilityHashMap, healthFacilityHashMap);
                                locationFinder.setHealthSubFacilityHashMap(healthSubFacilityHashMap);
                            }
                        }
                    }
                }
            }
        }
        LOGGER.debug("Locations processed. Records Processed : {}", count);

    }


    private void fillStates(HashMap<String, State> stateHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> stateKeys = stateHashMap.keySet();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<State>> queryExecution = new SqlQueryExecution<List<State>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT * from nms_states where";
                int count = stateKeys.size();
                for (String stateString : stateKeys) {
                    count--;
                    query += " code = " + stateString;
                    if (count > 0) {
                        query += " OR ";
                    }
                }

                LOGGER.debug("STATE Query: {}", query);
                return query;
            }

            @Override
            public List<State> execute(Query query) {
                query.setClass(State.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();
                List<State> states;
                if (fqr.isEmpty()) {
                    return null;
                }
//                Long count = 0L;
//                while (fqr.size() > count) {
//                    states.add((State) fqr.get(count.intValue()));
//                    count++;
//                }
                states = (List<State>) fqr;
                return states;
            }
        };

        List<State> states = stateDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("STATE Query time: {}", queryTimer.time());
        for (State state : states) {
            stateHashMap.put(state.getCode().toString(), state);
        }
    }

    private void fillDistricts(HashMap<String, District> districtHashMap, final HashMap<String, State> stateHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> districtKeys = districtHashMap.keySet();
        HashMap<Long, String> stateIdMap = new HashMap<>();
        for (String stateKey : stateHashMap.keySet()) {
            stateIdMap.put(stateHashMap.get(stateKey).getId(), stateKey);
        }


        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<District>> queryExecution = new SqlQueryExecution<List<District>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT * from nms_districts where";
                int count = districtKeys.size();
                for (String districtString : districtKeys) {
                    count--;
                    String[] ids = districtString.split("_");
                    Long stateId = stateHashMap.get(ids[0]).getId();
                    query += " (code = " + ids[1] + " and state_id_oid = " + stateId + ")";
                    if (count > 0) {
                        query += " OR ";
                    }
                }

                LOGGER.debug("DISTRICT Query: {}", query);
                return query;
            }

            @Override
            public List<District> execute(Query query) {
                query.setClass(District.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();
                List<District> districts;
                if (fqr.isEmpty()) {
                    return null;
                }
//                Long count = 0L;
//                while (fqr.size() > count) {
//                    districts.add((District) fqr.get(count.intValue()));
//                    count++;
//                }
                districts = (List<District>) fqr;
                return districts;
            }
        };

        List<District> districts = districtDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("DISTRICT Query time: {}", queryTimer.time());
        for (District district : districts) {
            String stateKey = stateIdMap.get(district.getState().getId());
            districtHashMap.put(stateKey + "_" + district.getCode(), district);
        }


    }


    private void createUpdateTalukas(final HashMap<String, Taluka> talukaHashMap, final HashMap<String, District> districtHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> talukaKeys = talukaHashMap.keySet();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "INSERT IGNORE into nms_talukas (code, district_id_oid, name, creationDate, modificationDate) values";
                int count = talukaKeys.size();
                for (String talukaString : talukaKeys) {
                    count--;
                    String[] ids = talukaString.split("_");
                    Long districtId = districtHashMap.get(ids[0] + "_" + ids[1]).getId();
                    query += " ( " + ids[2] + ", " + districtId + ", '" + talukaHashMap.get(talukaString).getName() +
                            "', ";
                    query += addDateColumns();
                    query += " )";
                    if (count > 0) {
                        query += " , ";
                    }
                }

                LOGGER.debug("TALUKA Query: {}", query);
                return query;
            }

            @Override
            public Long execute(Query query) {
                query.setClass(Taluka.class);
                return (Long) query.execute();
            }
        };

        Long talukaCount = talukaDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("TALUKAs inserted : {}", talukaCount);
        LOGGER.debug("TALUKA INSERT Query time: {}", queryTimer.time());
    }


    private void fillTalukas(HashMap<String, Taluka> talukaHashMap, final HashMap<String, District> districtHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> talukaKeys = talukaHashMap.keySet();
        HashMap<Long, String> districtIdMap = new HashMap<>();
        for (String districtKey : districtHashMap.keySet()) {
            districtIdMap.put(districtHashMap.get(districtKey).getId(), districtKey);
        }

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<Taluka>> queryExecution = new SqlQueryExecution<List<Taluka>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT * from nms_talukas where";
                int count = talukaKeys.size();
                for (String talukaString : talukaKeys) {
                    count--;
                    String[] ids = talukaString.split("_");
                    Long districtId = districtHashMap.get(ids[0] + "_" + ids[1]).getId();
                    query += " (code = " + ids[2] + " and district_id_oid = " + districtId + ")";
                    if (count > 0) {
                        query += " OR ";
                    }
                }

                LOGGER.debug("TALUKA Query: {}", query);
                return query;
            }

            @Override
            public List<Taluka> execute(Query query) {
                query.setClass(Taluka.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();
                List<Taluka> talukas;
                if (fqr.isEmpty()) {
                    return null;
                }
//                Long count = 0L;
//                while (fqr.size() > count) {
//                    talukas.add((Taluka) fqr.get(count.intValue()));
//                    count++;
//                }
                talukas = (List<Taluka>) fqr;
                return talukas;
            }
        };

        List<Taluka> talukas = talukaDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("TALUKA Query time: {}", queryTimer.time());
        for (Taluka taluka : talukas) {
            String districtKey = districtIdMap.get(taluka.getDistrict().getId());
            talukaHashMap.put(districtKey + "_" + taluka.getCode(), taluka);
        }
    }

    private void createUpdateVillages(final HashMap<String, Village> villageHashMap, final HashMap<String, Taluka> talukaHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> villageKeys = villageHashMap.keySet();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "INSERT IGNORE into nms_villages (vcode, svid,  taluka_id_oid, name, creationDate, modificationDate) values";
                int count = villageKeys.size();
                for (String villageString : villageKeys) {
                    count--;
                    String[] ids = villageString.split("_");
                    Long talukaId = talukaHashMap.get(ids[0] + "_" + ids[1] + "_" + ids[2]).getId();
                    query += " ( " + ids[3] + ", " + ids[4] + ", " + talukaId + ", '" +
                            villageHashMap.get(villageString).getName() + "', ";
                    query += addDateColumns();
                    query += " )";
                    if (count > 0) {
                        query += " , ";
                    }
                }

                LOGGER.debug("VILLAGE Query: {}", query);
                return query;
            }

            @Override
            public Long execute(Query query) {
                query.setClass(Village.class);
                return (Long) query.execute();
            }
        };

        Long villageCount = villageDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("VILLAGEs inserted : {}", villageCount);
        LOGGER.debug("VILLAGE INSERT Query time: {}", queryTimer.time());
    }

    private void fillVillages(HashMap<String, Village> villageHashMap, final HashMap<String, Taluka> talukaHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> villageKeys = villageHashMap.keySet();
        HashMap<Long, String> talukaIdMap = new HashMap<>();
        for (String districtKey : talukaHashMap.keySet()) {
            talukaIdMap.put(talukaHashMap.get(districtKey).getId(), districtKey);
        }

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<Village>> queryExecution = new SqlQueryExecution<List<Village>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT * from nms_villages where";
                int count = villageKeys.size();
                for (String villageString : villageKeys) {
                    count--;
                    String[] ids = villageString.split("_");
                    Long talukaId = talukaHashMap.get(ids[0] + "_" + ids[1] + "_" + ids[2]).getId();
                    query += " (vcode = " + ids[3] + "and svid = " + ids[4] + " and taluka_id_oid = " + talukaId + ")";
                    if (count > 0) {
                        query += " OR ";
                    }
                }

                LOGGER.debug("VILLAGE Query: {}", query);
                return query;
            }

            @Override
            public List<Village> execute(Query query) {
                query.setClass(Village.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();
                List<Village> villages;
                if (fqr.isEmpty()) {
                    return null;
                }
//                Long count = 0L;
//                while (fqr.size() > count) {
//                    villages.add((Village) fqr.get(count.intValue()));
//                    count++;
//                }
                villages = (List<Village>) fqr;
                return villages;
            }
        };

        List<Village> villages = villageDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("VILLAGE Query time: {}", queryTimer.time());
        for (Village village : villages) {
            String talukaKey = talukaIdMap.get(village.getTaluka().getId());
            villageHashMap.put(talukaKey + "_" + village.getVcode() + "_" + village.getSvid(), village);
        }
    }

    private void createUpdateHealthBlocks(final HashMap<String, HealthBlock> healthBlockHashMap, final HashMap<String, Taluka> talukaHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> healthBlockKeys = healthBlockHashMap.keySet();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "INSERT IGNORE into nms_health_blocks (code,  taluka_id_oid, name, creationDate, modificationDate) values";
                int count = healthBlockKeys.size();
                for (String healthBlockString : healthBlockKeys) {
                    count--;
                    String[] ids = healthBlockString.split("_");
                    Long talukaId = talukaHashMap.get(ids[0] + "_" + ids[1] + "_" + ids[2]).getId();
                    query += " ( " + ids[3] + ", " + talukaId + ", '" +
                            healthBlockHashMap.get(healthBlockString).getName() + "', ";
                    query += addDateColumns();
                    query += " )";
                    if (count > 0) {
                        query += " , ";
                    }
                }

                LOGGER.debug("HEALTHBLOCK Query: {}", query);
                return query;
            }

            @Override
            public Long execute(Query query) {
                query.setClass(HealthBlock.class);
                return (Long) query.execute();
            }
        };

        Long healthBlockCount = healthBlockDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("HEALTHBLOCKs inserted : {}", healthBlockCount);
        LOGGER.debug("HEALTHBLOCK INSERT Query time: {}", queryTimer.time());
    }

    private void fillHealthBlocks(HashMap<String, HealthBlock> healthBlockHashMap, final HashMap<String, Taluka> talukaHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> healthBlockKeys = healthBlockHashMap.keySet();
        HashMap<Long, String> talukaIdMap = new HashMap<>();
        for (String talukaKey : talukaHashMap.keySet()) {
            talukaIdMap.put(talukaHashMap.get(talukaKey).getId(), talukaKey);
        }

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<HealthBlock>> queryExecution = new SqlQueryExecution<List<HealthBlock>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT * from nms_health_blocks where";
                int count = healthBlockKeys.size();
                for (String villageString : healthBlockKeys) {
                    count--;
                    String[] ids = villageString.split("_");
                    Long talukaId = talukaHashMap.get(ids[0] + "_" + ids[1] + "_" + ids[2]).getId();
                    query += " (code = " + ids[3] +  " and taluka_id_oid = " + talukaId + ")";
                    if (count > 0) {
                        query += " OR ";
                    }
                }

                LOGGER.debug("HEALTHBLOCK Query: {}", query);
                return query;
            }

            @Override
            public List<HealthBlock> execute(Query query) {
                query.setClass(HealthBlock.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();
                List<HealthBlock> healthBlocks;
                if (fqr.isEmpty()) {
                    return null;
                }
//                Long count = 0L;
//                while (fqr.size() > count) {
//                    healthBlocks.add((HealthBlock) fqr.get(count.intValue()));
//                    count++;
//                }
                healthBlocks = (List<HealthBlock>) fqr;
                return healthBlocks;
            }
        };

        List<HealthBlock> healthBlocks = healthBlockDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("HEALTHBLOCK Query time: {}", queryTimer.time());
        for (HealthBlock healthBlock : healthBlocks) {
            String talukaKey = talukaIdMap.get(healthBlock.getTaluka().getId());
            healthBlockHashMap.put(talukaKey + "_" + healthBlock.getCode(), healthBlock);
        }
    }

    private void createUpdateHealthFacilities(final HashMap<String, HealthFacility> healthFacilityHashMap, final HashMap<String, HealthBlock> healthBlockHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> healthFacilityKeys = healthFacilityHashMap.keySet();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "INSERT IGNORE into nms_health_facilities (code,  healthBlock_id_oid, name, creationDate, modificationDate) values";
                int count = healthFacilityKeys.size();
                for (String healthFacilityString : healthFacilityKeys) {
                    count--;
                    String[] ids = healthFacilityString.split("_");
                    Long healthBlockId = healthBlockHashMap.get(ids[0] + "_" + ids[1] + "_" + ids[2] + "_" + ids[3]).getId();
                    query += " ( " + ids[4] + ", " + healthBlockId + ", '" +
                            healthFacilityHashMap.get(healthFacilityString).getName() + "', ";
                    query += addDateColumns();
                    query += " )";
                    if (count > 0) {
                        query += " , ";
                    }
                }

                LOGGER.debug("HEALTHFACILITY Query: {}", query);
                return query;
            }

            @Override
            public Long execute(Query query) {
                query.setClass(HealthFacility.class);
                return (Long) query.execute();
            }
        };

        Long healthFacilityCount = healthFacilityDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("HEALTHFACILITYs inserted : {}", healthFacilityCount);
        LOGGER.debug("HEALTHFACILITY INSERT Query time: {}", queryTimer.time());
    }

    private void fillHealthFacilities(HashMap<String, HealthFacility> healthFacilityHashMap, final HashMap<String, HealthBlock> healthBlockHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> healthFacilityKeys = healthFacilityHashMap.keySet();
        HashMap<Long, String> healthBlockIdMap = new HashMap<>();
        for (String healthBlockKey : healthBlockHashMap.keySet()) {
            healthBlockIdMap.put(healthBlockHashMap.get(healthBlockKey).getId(), healthBlockKey);
        }

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<HealthFacility>> queryExecution = new SqlQueryExecution<List<HealthFacility>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT * from nms_health_facilities where";
                int count = healthFacilityKeys.size();
                for (String healthFacilityString : healthFacilityKeys) {
                    count--;
                    String[] ids = healthFacilityString.split("_");
                    Long healthBlockId = healthBlockHashMap.get(ids[0] + "_" + ids[1] + "_" + ids[2] + "_" + ids[3]).getId();
                    query += " (code = " + ids[4] +  " and healthBlock_id_oid = " + healthBlockId + ")";
                    if (count > 0) {
                        query += " OR ";
                    }
                }

                LOGGER.debug("HEALTHFACILITY Query: {}", query);
                return query;
            }

            @Override
            public List<HealthFacility> execute(Query query) {
                query.setClass(HealthFacility.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();
                List<HealthFacility> healthFacilities;
                if (fqr.isEmpty()) {
                    return null;
                }
//                Long count = 0L;
//                while (fqr.size() > count) {
//                    healthFacilities.add((HealthFacility) fqr.get(count.intValue()));
//                    count++;
//                }
                healthFacilities = (List<HealthFacility>) fqr;
                return healthFacilities;
            }
        };

        List<HealthFacility> healthFacilities = healthFacilityDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("HEALTHFACILITY Query time: {}", queryTimer.time());
        for (HealthFacility healthFacility : healthFacilities) {
            String healthBlockKey = healthBlockIdMap.get(healthFacility.getHealthBlock().getId());
            healthFacilityHashMap.put(healthBlockKey + "_" + healthFacility.getCode(), healthFacility);
        }
    }

    private void createUpdateHealthSubFacilities(final HashMap<String, HealthSubFacility> healthSubFacilityHashMap, final HashMap<String, HealthFacility> healthFacilityHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> healthSubFacilityKeys = healthSubFacilityHashMap.keySet();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "INSERT IGNORE into nms_health_sub_facilities (code,  healthFacility_id_oid, name, creationDate, modificationDate) values";
                int count = healthSubFacilityKeys.size();
                for (String healthSubFacilityString : healthSubFacilityKeys) {
                    count--;
                    String[] ids = healthSubFacilityString.split("_");
                    Long healthFacilityId = healthFacilityHashMap.get(ids[0] + "_" + ids[1] + "_" + ids[2] + "_" + ids[3] + "_" + ids[4]).getId();
                    query += " ( " + ids[5] + ", " + healthFacilityId + ", '" +
                            healthSubFacilityHashMap.get(healthSubFacilityString).getName() + "', ";
                    query += addDateColumns();
                    query += " )";
                    if (count > 0) {
                        query += " , ";
                    }
                }

                LOGGER.debug("HEALTHSUBFACILITY Query: {}", query);
                return query;
            }

            @Override
            public Long execute(Query query) {
                query.setClass(HealthSubFacility.class);
                return (Long) query.execute();
            }
        };

        Long healthSubFacilityCount = healthSubFacilityDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("HEALTHSUBFACILITYs inserted : {}", healthSubFacilityCount);
        LOGGER.debug("HEALTHSUBFACILITY INSERT Query time: {}", queryTimer.time());
    }

    private void fillHealthSubFacilities(HashMap<String, HealthSubFacility> healthSubFacilityHashMap, final HashMap<String, HealthFacility> healthFacilityHashMap) {
        Timer queryTimer = new Timer();
        final Set<String> healthSubFacilityKeys = healthSubFacilityHashMap.keySet();
        HashMap<Long, String> healthFacilityIdMap = new HashMap<>();
        for (String healthFacilityKey : healthFacilityHashMap.keySet()) {
            healthFacilityIdMap.put(healthFacilityHashMap.get(healthFacilityKey).getId(), healthFacilityKey);
        }

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<HealthSubFacility>> queryExecution = new SqlQueryExecution<List<HealthSubFacility>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT * from nms_health_sub_facilities where";
                int count = healthSubFacilityKeys.size();
                for (String healthFacilityString : healthSubFacilityKeys) {
                    count--;
                    String[] ids = healthFacilityString.split("_");
                    Long healthFacilityId = healthFacilityHashMap.get(ids[0] + "_" + ids[1] + "_" + ids[2] + "_" + ids[3] + "_" + ids[4]).getId();
                    query += " (code = " + ids[5] +  " and healthFacility_id_oid = " + healthFacilityId + ")";
                    if (count > 0) {
                        query += " OR ";
                    }
                }

                LOGGER.debug("HEALTHSUBFACILITY Query: {}", query);
                return query;
            }

            @Override
            public List<HealthSubFacility> execute(Query query) {
                query.setClass(HealthSubFacility.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();
                List<HealthSubFacility> healthSubFacilities;
                if (fqr.isEmpty()) {
                    return null;
                }
//                Long count = 0L;
//                while (fqr.size() > count) {
//                    healthSubFacilities.add((HealthSubFacility) fqr.get(count.intValue()));
//                    count++;
//                }
                healthSubFacilities = (List<HealthSubFacility>) fqr;
                return healthSubFacilities;
            }
        };

        List<HealthSubFacility> healthFacilities = healthSubFacilityDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("HEALTHSUBFACILITY Query time: {}", queryTimer.time());
        for (HealthSubFacility healthSubFacility : healthFacilities) {
            String healthFacilityKey = healthFacilityIdMap.get(healthSubFacility.getHealthFacility().getId());
            healthSubFacilityHashMap.put(healthFacilityKey + "_" + healthSubFacility.getCode(), healthSubFacility);
        }
    }

    private String addDateColumns() {
        DateTime dateTimeNow = new DateTime();
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(DATE_FORMAT_STRING);
        String query = "";
        query += "'" + dateTimeFormatter.print(dateTimeNow) + "'";
        query += ", ";
        query += "'" + dateTimeFormatter.print(dateTimeNow) + "'";
        return query;
    }

}
