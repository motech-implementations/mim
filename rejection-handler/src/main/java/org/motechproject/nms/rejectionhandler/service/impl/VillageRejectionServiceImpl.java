package org.motechproject.nms.rejectionhandler.service.impl;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.nms.rejectionhandler.domain.HealthFacilityImportRejection;
import org.motechproject.nms.rejectionhandler.domain.VillageImportRejection;
import org.motechproject.nms.rejectionhandler.repository.VillageRejectionDataService;
import org.motechproject.nms.rejectionhandler.service.VillageRejectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.jdo.Query;

@Service("villageRejectionService")
public class VillageRejectionServiceImpl implements VillageRejectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VillageRejectionServiceImpl.class);
    private static final String MOTECH_STRING = "'motech', ";
    private static final String DATE_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private VillageRejectionDataService villageRejectionDataService;


    @Override
    public Long saveRejectedVillage(VillageImportRejection villageImportRejection) {
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {
            @Override
            public String getSqlQuery() {
                DateTime dateTimeNow = new DateTime();
                DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(DATE_FORMAT_STRING);
                LOGGER.info("In getSqlQuery");
                String healthBlockValues = "(" + villageImportRejection.getStateId() + ", " + villageImportRejection.getDistrictCode() + ", '" + villageImportRejection.getTalukaCode() + "', " +
                        villageImportRejection.getVillageCode()+", "+villageImportRejection.getVillageName()+", "  + villageImportRejection.getAccepted() + ", '" + villageImportRejection.getRejectionReason() +"', "+MOTECH_STRING+MOTECH_STRING+MOTECH_STRING+"'"+ dateTimeFormatter.print(dateTimeNow)+"', '"+dateTimeFormatter.print(dateTimeNow)+"')";
                LOGGER.info(healthBlockValues);
                String query = "INSERT into nms_health_block_rejects (`stateId`, `districtCode`, `talukaCode`, `villageCode`, `villageName`," +
                        " `accepted`, `rejectionReason`, `creator`, `modifiedBy`, `owner`, `creationDate`, `modificationDate`) VALUES " +
                        healthBlockValues + " ON DUPLICATE KEY UPDATE " +
                        "districtCode = VALUES(districtCode), talukaCode = VALUES(talukaCode), villageName = VALUES(villageName), accepted = VALUES(accepted), rejectionReason = VALUES(rejectionReason),modificationDate = VALUES(modificationDate), modifiedBy = VALUES(modifiedBy) ";
                LOGGER.info("Printing Query for rejected Village: "+ query);
                return query;


            }
            @Override
            public Long execute(Query query) {
                query.setClass(HealthFacilityImportRejection.class);
                LOGGER.info("Village class reject query: " + query.toString());
                return (Long) query.execute();
            }

        };
        Long rejectedVillage = 0L;
        LOGGER.info("Printing Village rejection query execution: " + queryExecution.toString());

        rejectedVillage = villageRejectionDataService.executeSQLQuery(queryExecution);

        return rejectedVillage;
    }
    @Override
    public void createRejectedVillage(VillageImportRejection villageImportRejection) {

    }
}