package org.motechproject.nms.region.service.impl;

import org.apache.commons.lang.StringEscapeUtils;
import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.metrics.service.Timer;
import org.motechproject.nms.region.domain.HealthBlock;
import org.motechproject.nms.region.domain.HealthFacility;
import org.motechproject.nms.region.domain.Taluka;
import org.motechproject.nms.region.repository.HealthFacilityDataService;
import org.motechproject.nms.region.service.HealthFacilityService;
import org.motechproject.nms.region.utils.LocationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.jdo.Query;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service("healthFacilityService")
public class HealthFacilityServiceImpl implements HealthFacilityService {

    private static final String QUOTATION = "'";
    private static final String QUOTATION_COMMA = "', ";
    private static final String MOTECH_STRING = "'motech', ";
    private static final String SQL_QUERY_LOG = "SQL QUERY: {}";
    private static final String DATE_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss";

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthFacilityServiceImpl.class);


    @Autowired
    private HealthFacilityDataService dataService;

    @Override
    public HealthFacility findByHealthBlockAndCode(final HealthBlock healthBlock, final Long code) {
        if (healthBlock == null) { return null; }

        SqlQueryExecution<HealthFacility> queryExecution = new SqlQueryExecution<HealthFacility>() {

            @Override
            public String getSqlQuery() {
                return "select *  from nms_health_facilities where healthBlock_id_oid = ? and code = ?";
            }

            @Override
            public HealthFacility execute(Query query) {
                query.setClass(HealthFacility.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute(healthBlock.getId(), code);
                if (fqr.isEmpty()) {
                    return null;
                }
                if (fqr.size() == 1) {
                    return (HealthFacility) fqr.get(0);
                }
                throw new IllegalStateException("More than one row returned!");
            }
        };

        return dataService.executeSQLQuery(queryExecution);
    }

    @Override
    public HealthFacility create(HealthFacility healthFacility) {
        return dataService.create(healthFacility);
    }

    @Override
    public HealthFacility update(HealthFacility healthFacility) {
        return dataService.update(healthFacility);
    }

    @Override
    public Long createUpdateHealthFacilities(final List<Map<String, Object>> healthFacilities, final Map<String, Taluka> talukaHashMap, final Map<String, HealthBlock> healthBlockHashMap) {
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "INSERT into nms_health_blocks (`code`, `name`, `healthBlock_id_OID`, `taluka_id_oid`, " +
                        " `creator`, `modifiedBy`, `owner`, `creationDate`, `modificationDate`) VALUES " +
                        healthFacilityQuerySet(healthFacilities, talukaHashMap, healthBlockHashMap) +
                        " ON DUPLICATE KEY UPDATE " +
                        "name = VALUES(name), modificationDate = VALUES(modificationDate), modifiedBy = VALUES(modifiedBy) ";
                LOGGER.debug(SQL_QUERY_LOG, query);
                return query;
            }



            @Override
            public Long execute(Query query) {
                query.setClass(HealthFacility.class);
                return (Long) query.execute();
            }
        };

        Long createdDistricts = dataService.executeSQLQuery(queryExecution);


        return createdDistricts;
    }

    @Override
    public Map<String, HealthFacility> fillHealthFacilityIds(List<Map<String, Object>> recordList, final Map<String, HealthBlock> healthBlockHashMap) {
        final Set<String> healthFacilityKeys = new HashSet<>();
        for(Map<String, Object> record : recordList) {
            healthFacilityKeys.add(record.get(LocationConstants.STATE_ID).toString() + "_" + record.get(LocationConstants.DISTRICT_ID).toString() + "_" +
                    record.get(LocationConstants.TALUKA_ID).toString() + "_" + record.get(LocationConstants.HEALTHBLOCK_ID).toString() + "_" +
                    record.get(LocationConstants.HEALTHFACILITY_ID).toString());
        }
        Map<String, HealthFacility> healthFacilityHashMap = new HashMap<>();

        Map<Long, String> healthBlockIdMap = new HashMap<>();
        for (String healthBlockKey : healthBlockHashMap.keySet()) {
            healthBlockIdMap.put(healthBlockHashMap.get(healthBlockKey).getId(), healthBlockKey);
        }

        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<HealthFacility>> queryExecution = new SqlQueryExecution<List<HealthFacility>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT * from nms_health_facilities where";
                int count = healthFacilityKeys.size();
                for (String healthFacilityString : healthFacilityKeys) {
                    count--;
                    String[] ids = healthFacilityString.split("_");
                    Long healthBlockId = healthBlockHashMap.get(ids[0] + "_" + ids[1] + "_" + ids[3]).getId();
                    query += LocationConstants.CODE_SQL_STRING + ids[4] +  " and healthBlock_id_oid = " + healthBlockId + ")";
                    if (count > 0) {
                        query += LocationConstants.OR_SQL_STRING;
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
                healthFacilities = (List<HealthFacility>) fqr;
                return healthFacilities;
            }
        };

        List<HealthFacility> healthFacilities = dataService.executeSQLQuery(queryExecution);
        LOGGER.debug("HEALTHFACILITY Query time: {}", queryTimer.time());
        if(healthFacilities != null && !healthFacilities.isEmpty()) {
            for (HealthFacility healthFacility : healthFacilities) {
                String healthBlockKey = healthBlockIdMap.get(healthFacility.getHealthBlock().getId());
                healthFacilityHashMap.put(healthBlockKey + "_" + healthFacility.getCode(), healthFacility);
            }
        }
        return healthFacilityHashMap;
    }

    private String healthFacilityQuerySet(List<Map<String, Object>> healthFacilities, Map<String, Taluka> talukaHashMap, Map<String, HealthBlock> healthBlockHashMap) {
        StringBuilder stringBuilder = new StringBuilder();
        int i = 0;
        DateTime dateTimeNow = new DateTime();
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(DATE_FORMAT_STRING);
        for (Map<String, Object> healthFacility : healthFacilities) {
            if (i != 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("(");
            stringBuilder.append(healthFacility.get(LocationConstants.HEALTHFACILITY_ID) + ", ");
            stringBuilder.append(QUOTATION + StringEscapeUtils.escapeSql(healthFacility.get(LocationConstants.HEALTHFACILITY_NAME).toString()) + QUOTATION_COMMA);
            stringBuilder.append(healthBlockHashMap.get(healthFacility.get(LocationConstants.STATE_ID).toString() + "_" +
                    healthFacility.get(LocationConstants.DISTRICT_ID).toString() + "_" +
                    healthFacility.get(LocationConstants.HEALTHBLOCK_ID).toString()).getId() + ", ");
            stringBuilder.append(talukaHashMap.get(healthFacility.get(LocationConstants.STATE_ID).toString() + "_" +
                    healthFacility.get(LocationConstants.DISTRICT_ID).toString() + "_" +
                    healthFacility.get(LocationConstants.TALUKA_ID).toString()).getId() + ", ");
            stringBuilder.append(MOTECH_STRING);
            stringBuilder.append(MOTECH_STRING);
            stringBuilder.append(MOTECH_STRING);
            stringBuilder.append(QUOTATION + dateTimeFormatter.print(dateTimeNow) + QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + dateTimeFormatter.print(dateTimeNow) + QUOTATION);
            stringBuilder.append(")");

            i++;
        }

        return stringBuilder.toString();
    }
}
