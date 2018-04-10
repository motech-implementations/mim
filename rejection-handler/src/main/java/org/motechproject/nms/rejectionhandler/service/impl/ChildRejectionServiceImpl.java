package org.motechproject.nms.rejectionhandler.service.impl;

import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.metrics.service.Timer;
import org.motechproject.nms.rejectionhandler.domain.ChildImportRejection;
import org.motechproject.nms.rejectionhandler.repository.ChildRejectionDataService;
import org.motechproject.nms.rejectionhandler.service.ChildRejectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.jdo.Query;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;

import static org.motechproject.nms.tracking.utils.TrackChangeUtils.LOGGER;

@Service("childRejectionService")
public class ChildRejectionServiceImpl implements ChildRejectionService {

    @Autowired
    private ChildRejectionDataService childRejectionDataService;

    private static final String quotation = "'";
    private static final String quotationComma = "', ";

    @Override //NO CHECKSTYLE CyclomaticComplexity
    public boolean createOrUpdateChild(ChildImportRejection childImportRejection) {
        if (childImportRejection.getIdNo() != null || childImportRejection.getRegistrationNo() != null) {

            ChildImportRejection childRejectionRecord;
            if ("RCH-Import".equals(childImportRejection.getSource())) {
                childRejectionRecord = childRejectionDataService.findByRegistrationNo(childImportRejection.getRegistrationNo());
            } else {
                childRejectionRecord = childRejectionDataService.findByIdno(childImportRejection.getIdNo());
            }

            if (childRejectionRecord == null && !childImportRejection.getAccepted()) {
                childRejectionDataService.create(childImportRejection);
                return true;
            } else if (childRejectionRecord == null && childImportRejection.getAccepted()) {
                LOGGER.debug(String.format("There is no mother rejection data for mctsId %s and rchId %s", childImportRejection.getIdNo(), childImportRejection.getRegistrationNo()));
            } else {
                // If found one in database, update the record with new data
                childRejectionRecord = setNewData1(childImportRejection, childRejectionRecord);
                childRejectionDataService.update(childRejectionRecord);
                return false;
            }
        }
        return true;
    }

    @Override
    public Map<String, Object> findChildRejectionByRchId(final Set<String> rchIds) {
        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Map<String, Object>> queryExecution = new SqlQueryExecution<Map<String, Object>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT id, registrationNo, creationDate FROM nms_child_rejects WHERE registrationNo IN " + queryRchList(rchIds);
                LOGGER.debug("SQL QUERY: {}", query);
                return query;
            }

            @Override
            public Map<String, Object> execute(Query query) {

                query.setClass(ChildImportRejection.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();
                Map<String, Object> resultMap = new HashMap<>();
                for (ChildImportRejection childReject : (List<ChildImportRejection>) fqr) {
                    resultMap.put(childReject.getRegistrationNo(), childReject);
                }
                return resultMap;
            }
        };

        Map<String, Object> resultMap = childRejectionDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("List of child rejects in {}", queryTimer.time());
        return resultMap;
    }

    private String queryRchList(Set<String> rchList) {
        StringBuilder stringBuilder = new StringBuilder();
        int i = 0;
        stringBuilder.append("(");
        for (String rch: rchList) {
            if (i != 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(quotation + rch + quotation);
            i++;
        }
        stringBuilder.append(")");

        return stringBuilder.toString();
    }

    public Long bulkInsert(final List<ChildImportRejection> createObjects) {

        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "Insert into nms_child_rejects (subcentreId, subcentreName, villageId, villageName, name, mobileNo, stateId, districtId, districtName, talukaId," +
                        " talukaName, healthBlockId, healthBlockName, phcId, phcName, birthDate, registrationNo, entryType, idNo, mCTSMotherIDNo, rCHMotherIDNo, execDate, source," +
                        " accepted, rejectionReason, action, creator, modifiedBy, creationDate, modificationDate) " +
                        "values  " +
                        rchChildToQuerySet(createObjects);

                LOGGER.debug("SQL QUERY: {}", query);
                return query;
            }

            @Override
            public Long execute(Query query) {
                query.setClass(ChildImportRejection.class);
                return (Long) query.execute();
            }
        };

        Long insertedNo = childRejectionDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("List of child rejects in {}", queryTimer.time());
        return insertedNo;
    }

    private String rchChildToQuerySet(List<ChildImportRejection> childList) {
        StringBuilder stringBuilder = new StringBuilder();

        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime dateTimeNow = new DateTime();

        int i = 0;
        for (ChildImportRejection child: childList) {
            if (i != 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("(");
            stringBuilder.append(child.getSubcentreId() + ", ");
            stringBuilder.append(quotation + child.getSubcentreName() + quotationComma);
            stringBuilder.append(child.getVillageId() + ", ");
            stringBuilder.append(quotation + child.getVillageName() + quotationComma);
            stringBuilder.append(quotation + child.getName() + quotationComma);
            stringBuilder.append(quotation + child.getMobileNo() + quotationComma);
            stringBuilder.append(child.getStateId() + ", ");
            stringBuilder.append(child.getDistrictId() + ", ");
            stringBuilder.append(quotation + child.getDistrictName() + quotationComma);
            stringBuilder.append(quotation + child.getTalukaId() + quotationComma);
            stringBuilder.append(quotation + child.getTalukaName() + quotationComma);
            stringBuilder.append(child.getHealthBlockId() + ", ");
            stringBuilder.append(quotation + child.getHealthBlockName() + quotationComma);
            stringBuilder.append(child.getPhcId() + ", ");
            stringBuilder.append(quotation + child.getPhcName() + quotationComma);
            stringBuilder.append(quotation + child.getBirthDate() + quotationComma);
            stringBuilder.append(quotation + child.getRegistrationNo() + quotationComma);
            stringBuilder.append(child.getEntryType() + ", ");
            stringBuilder.append(quotation + child.getIdNo() + quotationComma);
            stringBuilder.append(quotation + child.getmCTSMotherIDNo() + quotationComma);
            stringBuilder.append(quotation + child.getMotherId() + quotationComma);
            stringBuilder.append(quotation + child.getExecDate() + quotationComma);
            stringBuilder.append(quotation + child.getSource() + quotationComma);
            stringBuilder.append(child.getAccepted() + ", ");
            stringBuilder.append(quotation + child.getRejectionReason() + quotationComma);
            stringBuilder.append(quotation + child.getAction() + quotationComma);
            stringBuilder.append("'motech', ");
            stringBuilder.append("'motech', ");
            stringBuilder.append(quotation + dateTimeFormatter.print(dateTimeNow) + quotationComma);
            stringBuilder.append(quotation + dateTimeFormatter.print(dateTimeNow) + quotation);
            stringBuilder.append(")");
            i++;
        }
        return stringBuilder.toString();
    }
    private String mctsChildToQuerySet(List<ChildImportRejection> childList) {
        StringBuilder stringBuilder = new StringBuilder();
//        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        Date date = new Date();

        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime dateTimeNow = new DateTime();

        int i = 0;
        for (ChildImportRejection child: childList) {
            if (i != 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("(");
            stringBuilder.append(child.getStateId() + ", ");
            stringBuilder.append(child.getDistrictId() + ", ");
            stringBuilder.append("'" + child.getDistrictName() + "', ");
            stringBuilder.append(child.getTalukaId() + ", ");
            stringBuilder.append("'" + child.getTalukaName() + "', ");
            stringBuilder.append(child.getHealthBlockId() + ", ");
            stringBuilder.append("'" + child.getHealthBlockName() + "', ");
            stringBuilder.append(child.getPhcId() + ", ");
            stringBuilder.append("'" + child.getPhcName() + "', ");
            stringBuilder.append(child.getSubcentreId() + ", ");
            stringBuilder.append("'" + child.getSubcentreName() + "', ");
            stringBuilder.append(child.getVillageId() + ", ");
            stringBuilder.append("'" + child.getVillageName() + "', ");
            stringBuilder.append("'" + child.getYr() + "', ");
            stringBuilder.append("'" + child.getCityMaholla() + "', ");
            stringBuilder.append("'" + child.getgPVillage() + "', ");
            stringBuilder.append("'" + child.getAddress() + "', ");
            stringBuilder.append("'" + child.getIdNo() + "', ");
            stringBuilder.append("'" + child.getName() + "', ");
            stringBuilder.append("'" + child.getMotherName() + "', ");
            stringBuilder.append("'" + child.getMotherId() + "', ");
            stringBuilder.append("'" + child.getPhoneNumberWhom() + "', ");
            stringBuilder.append("'" + child.getMobileNo() + "', ");
            stringBuilder.append("'" + child.getBirthDate() + "', ");
            stringBuilder.append("'" + child.getPlaceOfDelivery() + "', ");
            stringBuilder.append("'" + child.getBloodGroup() + "', ");
            stringBuilder.append("'" + child.getCaste() + "', ");
            stringBuilder.append("'" + child.getSubcenterName1() + "', ");
            stringBuilder.append("'" + child.getaNMName() + "', ");
            stringBuilder.append("'" + child.getaNMPhone() + "', ");
            stringBuilder.append("'" + child.getAshaName() + "', ");
            stringBuilder.append("'" + child.getAshaPhone() + "', ");
            stringBuilder.append("'" + child.getbCGDt() + "', ");
            stringBuilder.append("'" + child.getoPV0Dt() + "', ");
            stringBuilder.append("'" + child.getHepatitisB1Dt() + "', ");
            stringBuilder.append("'" + child.getdPT1Dt() + "', ");
            stringBuilder.append("'" + child.getoPV1Dt() + "', ");
            stringBuilder.append("'" + child.getHepatitisB2Dt() + "', ");
            stringBuilder.append("'" + child.getdPT2Dt() + "', ");
            stringBuilder.append("'" + child.getoPV2Dt() + "', ");
            stringBuilder.append("'" + child.getHepatitisB3Dt() + "', ");
            stringBuilder.append("'" + child.getdPT3Dt() + "', ");
            stringBuilder.append("'" + child.getoPV3Dt() + "', ");
            stringBuilder.append("'" + child.getHepatitisB4Dt() + "', ");
            stringBuilder.append("'" + child.getMeaslesDt() + "', ");
            stringBuilder.append("'" + child.getVitADose1Dt() + "', ");
            stringBuilder.append("'" + child.getmRDt() + "', ");
            stringBuilder.append("'" + child.getdPTBoosterDt() + "', ");
            stringBuilder.append("'" + child.getoPVBoosterDt() + "', ");
            stringBuilder.append("'" + child.getVitADose2Dt() + "', ");
            stringBuilder.append("'" + child.getVitADose3Dt() + "', ");
            stringBuilder.append("'" + child.getjEDt() + "', ");
            stringBuilder.append("'" + child.getVitADose9Dt() + "', ");
            stringBuilder.append("'" + child.getdT5Dt() + "', ");
            stringBuilder.append("'" + child.gettT10Dt() + "', ");
            stringBuilder.append("'" + child.gettT16Dt() + "', ");
            stringBuilder.append("'" + child.getcLDRegDATE() + "', ");
            stringBuilder.append("'" + child.getSex() + "', ");
            stringBuilder.append("'" + child.getVitADose5Dt() + "', ");
            stringBuilder.append("'" + child.getVitADose6Dt() + "', ");
            stringBuilder.append("'" + child.getVitADose7Dt() + "', ");
            stringBuilder.append("'" + child.getVitADose8Dt() + "', ");
            stringBuilder.append("'" + child.getLastUpdateDate() + "', ");
            stringBuilder.append("'" + child.getRemarks() + "', ");
            stringBuilder.append("'" + child.getaNMID() + "', ");
            stringBuilder.append("'" + child.getAshaID() + "', ");
            stringBuilder.append("'" + child.getCreatedBy() + "', ");
            stringBuilder.append("'" + child.getUpdatedBy() + "', ");
            stringBuilder.append("'" + child.getMeasles2Dt() + "', ");
            stringBuilder.append("'" + child.getWeightOfChild() + "', ");
            stringBuilder.append("'" + child.getChildAadhaarNo() + "', ");
            stringBuilder.append("'" + child.getChildEID() + "', ");
            stringBuilder.append("'" + child.getChildEIDTime() + "', ");
            stringBuilder.append("'" + child.getFatherName() + "', ");
            stringBuilder.append("'" + child.getBirthCertificateNumber() + "', ");
            stringBuilder.append(child.getEntryType() + ", ");
            stringBuilder.append("'" + child.getSource() + "', ");
            stringBuilder.append("'" + child.getSource() + "', ");
            stringBuilder.append(child.getAccepted() + ", ");
            stringBuilder.append("'" + child.getRejectionReason() + "', ");
            stringBuilder.append("'" + child.getAction() + "', ");
            stringBuilder.append("'motech', ");
            stringBuilder.append("'motech', ");
            stringBuilder.append("'" + dateTimeFormatter.print(dateTimeNow) + "', ");
            stringBuilder.append("'" + dateTimeFormatter.print(dateTimeNow) + "'");
            stringBuilder.append(")");
            i++;
        }

        String valueQuery = stringBuilder.toString();
        return valueQuery;
    }

    public Long bulkUpdate(final List<ChildImportRejection> updateObjects) {

        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "Insert into nms_child_rejects (id, subcentreId, subcentreName, villageId, villageName, name, mobileNo, stateId, districtId, districtName, talukaId," +
                        " talukaName, healthBlockId, healthBlockName, phcId, phcName, birthDate, registrationNo, entryType, idNo, mCTSMotherIDNo, rCHMotherIDNo, execDate, source," +
                        " accepted, rejectionReason, action, modifiedBy, creationDate, modificationDate, creator) " +
                        "values  " +
                        childUpdateQuerySet(updateObjects) +
                        " ON DUPLICATE KEY UPDATE " +
                        "subcentreId = VALUES(subcentreId),  subcentreName = VALUES(subcentreName),  villageId = VALUES(villageId),  villageName = VALUES(villageName),  " +
                        "name = VALUES(name),  mobileNo = VALUES(mobileNo),  stateId = VALUES(stateId),  districtId = VALUES(districtId),  districtName = VALUES(districtName),  talukaId = VALUES(talukaId),  talukaName = VALUES(talukaName),  " +
                        "healthBlockId = VALUES(healthBlockId),  healthBlockName = VALUES(healthBlockName),  phcId = VALUES(phcId),  phcName = VALUES(phcName),  birthDate = VALUES(birthDate),  entryType = VALUES(entryType),  " +
                        "idNo = VALUES(idNo),  mCTSMotherIDNo = VALUES(mCTSMotherIDNo),  rCHMotherIDNo = VALUES(rCHMotherIDNo),  execDate = VALUES(execDate),  source = VALUES(source),  " +
                        "accepted = VALUES(accepted),  rejectionReason = VALUES(rejectionReason),  action = VALUES(action),  modifiedBy = VALUES(modifiedBy), creationDate = VALUES(creationDate), modificationDate = VALUES(modificationDate), creator = VALUES(creator)";

                LOGGER.debug("SQL QUERY: {}", query);
                return query;
            }

            @Override
            public Long execute(Query query) {

                query.setClass(ChildImportRejection.class);
                return (Long) query.execute();
            }
        };

        Long updatedNo = childRejectionDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("List of child rejects in {}", queryTimer.time());
        return updatedNo;
    }

    private String childUpdateQuerySet(List<ChildImportRejection> childList) { //NOPMD NcssMethodCount
        StringBuilder stringBuilder = new StringBuilder();
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime dateTimeNow = new DateTime();
        int i = 0;
        for (ChildImportRejection child: childList) {
            String creationTime = "";
            Long id = null;
            if (i != 0) {
                stringBuilder.append(", ");
            }
            try {
                Method method = child.getClass().getMethod("getCreationDate");
                DateTime dateTime = (DateTime) method.invoke(child);
                creationTime = dateTimeFormatter.print(dateTime);

                method = child.getClass().getMethod("getId");
                id = (Long) method.invoke(child);
            } catch (IllegalAccessException|SecurityException|IllegalArgumentException|NoSuchMethodException|
            InvocationTargetException e) {
                LOGGER.error("Ignoring creation date and setting as now");
            }
            stringBuilder.append("(");
            stringBuilder.append(id + ", ");
            stringBuilder.append(child.getSubcentreId() + ", ");
            stringBuilder.append(quotation + child.getSubcentreName() + quotationComma);
            stringBuilder.append(child.getVillageId() + ", ");
            stringBuilder.append(quotation + child.getVillageName() + quotationComma);
            stringBuilder.append(quotation + child.getName() + quotationComma);
            stringBuilder.append(quotation + child.getMobileNo() + quotationComma);
            stringBuilder.append(child.getStateId() + ", ");
            stringBuilder.append(child.getDistrictId() + ", ");
            stringBuilder.append(quotation + child.getDistrictName() + quotationComma);
            stringBuilder.append(quotation + child.getTalukaId() + quotationComma);
            stringBuilder.append(quotation + child.getTalukaName() + quotationComma);
            stringBuilder.append(child.getHealthBlockId() + ", ");
            stringBuilder.append(quotation + child.getHealthBlockName() + quotationComma);
            stringBuilder.append(child.getPhcId() + ", ");
            stringBuilder.append(quotation + child.getPhcName() + quotationComma);
            stringBuilder.append(quotation + child.getBirthDate() + quotationComma);
            stringBuilder.append(quotation + child.getRegistrationNo() + quotationComma);
            stringBuilder.append(child.getEntryType() + ", ");
            stringBuilder.append(quotation + child.getIdNo() + quotationComma);
            stringBuilder.append(quotation + child.getmCTSMotherIDNo() + quotationComma);
            stringBuilder.append(quotation + child.getMotherId() + quotationComma);
            stringBuilder.append(quotation + child.getExecDate() + quotationComma);
            stringBuilder.append(quotation + child.getSource() + quotationComma);
            stringBuilder.append(child.getAccepted() + ", ");
            stringBuilder.append(quotation + child.getRejectionReason() + quotationComma);
            stringBuilder.append(quotation + child.getAction() + quotationComma);
            stringBuilder.append("'motech', ");
            stringBuilder.append(quotation + creationTime + quotationComma);
            stringBuilder.append(quotation + dateTimeFormatter.print(dateTimeNow) + quotationComma);
            stringBuilder.append("'motech'");
            stringBuilder.append(")");
            i++;
        }
        return stringBuilder.toString();
    }


    private static ChildImportRejection setNewData1(ChildImportRejection childImportRejection, ChildImportRejection childImportRejection1) {
        childImportRejection1.setStateId(childImportRejection.getStateId());
        childImportRejection1.setDistrictId(childImportRejection.getDistrictId());
        childImportRejection1.setDistrictName(childImportRejection.getDistrictName());
        childImportRejection1.setTalukaId(childImportRejection.getTalukaId());
        childImportRejection1.setTalukaName(childImportRejection.getTalukaName());
        childImportRejection1.setHealthBlockId(childImportRejection.getHealthBlockId());
        childImportRejection1.setHealthBlockName(childImportRejection.getHealthBlockName());
        childImportRejection1.setPhcId(childImportRejection.getPhcId());
        childImportRejection1.setPhcName(childImportRejection.getPhcName());
        childImportRejection1.setSubcentreId(childImportRejection.getSubcentreId());
        childImportRejection1.setSubcentreName(childImportRejection.getSubcentreName());
        childImportRejection1.setVillageId(childImportRejection.getVillageId());
        childImportRejection1.setVillageName(childImportRejection.getVillageName());
        childImportRejection1.setYr(childImportRejection.getYr());
        childImportRejection1.setCityMaholla(childImportRejection.getCityMaholla());
        childImportRejection1.setgPVillage(childImportRejection.getgPVillage());
        childImportRejection1.setAddress(childImportRejection.getAddress());
        childImportRejection1.setIdNo(childImportRejection.getIdNo());
        childImportRejection1.setName(childImportRejection.getName());
        childImportRejection1.setMobileNo(childImportRejection.getMobileNo());
        childImportRejection1.setMotherName(childImportRejection.getMotherName());
        childImportRejection1.setMotherId(childImportRejection.getMotherId());
        childImportRejection1.setAshaPhone(childImportRejection.getAshaPhone());
        childImportRejection1.setbCGDt(childImportRejection.getbCGDt());
        childImportRejection1.setoPV0Dt(childImportRejection.getoPV0Dt());
        childImportRejection1.setHepatitisB1Dt(childImportRejection.getHepatitisB1Dt());
        childImportRejection1.setdPT1Dt(childImportRejection.getdPT1Dt());
        childImportRejection1.setoPV1Dt(childImportRejection.getoPV1Dt());
        childImportRejection1.setHepatitisB2Dt(childImportRejection.getHepatitisB2Dt());
        childImportRejection1.setPhoneNumberWhom(childImportRejection.getPhoneNumberWhom());
        childImportRejection1.setBirthDate(childImportRejection.getBirthDate());
        childImportRejection1.setPlaceOfDelivery(childImportRejection.getPlaceOfDelivery());
        childImportRejection1.setBloodGroup(childImportRejection.getBloodGroup());
        childImportRejection1.setCaste(childImportRejection.getCaste());
        childImportRejection1.setSubcenterName1(childImportRejection1.getSubcenterName1());
        childImportRejection1.setaNMName(childImportRejection.getaNMName());
        childImportRejection1.setaNMPhone(childImportRejection.getaNMPhone());
        childImportRejection1.setAshaName(childImportRejection.getAshaName());
        childImportRejection1.setdPT2Dt(childImportRejection.getdPT2Dt());
        childImportRejection1.setoPV2Dt(childImportRejection.getoPV2Dt());
        setNewData2(childImportRejection, childImportRejection1);
        return childImportRejection1;
    }

    private static void setNewData2(ChildImportRejection childImportRejection, ChildImportRejection childImportRejection1) {
        childImportRejection1.setHepatitisB3Dt(childImportRejection.getHepatitisB3Dt());
        childImportRejection1.setdPT3Dt(childImportRejection.getdPT3Dt());
        childImportRejection1.setoPV3Dt(childImportRejection.getoPV3Dt());
        childImportRejection1.setHepatitisB4Dt(childImportRejection.getHepatitisB4Dt());
        childImportRejection1.setMeaslesDt(childImportRejection.getMeaslesDt());
        childImportRejection1.setVitADose1Dt(childImportRejection.getVitADose1Dt());
        childImportRejection1.setmRDt(childImportRejection.getmRDt());
        childImportRejection1.setdPTBoosterDt(childImportRejection.getdPTBoosterDt());
        childImportRejection1.setoPVBoosterDt(childImportRejection.getoPVBoosterDt());
        childImportRejection1.setVitADose2Dt(childImportRejection.getVitADose2Dt());
        childImportRejection1.setVitADose3Dt(childImportRejection.getVitADose3Dt());
        childImportRejection1.settT10Dt(childImportRejection.gettT10Dt());
        childImportRejection1.setjEDt(childImportRejection.getjEDt());
        childImportRejection1.setVitADose9Dt(childImportRejection.getVitADose9Dt());
        childImportRejection1.setdT5Dt(childImportRejection.getdT5Dt());
        childImportRejection1.settT16Dt(childImportRejection.gettT16Dt());
        childImportRejection1.setcLDRegDATE(childImportRejection.getcLDRegDATE());
        childImportRejection1.setAshaID(childImportRejection.getAshaID());
        childImportRejection1.setLastUpdateDate(childImportRejection.getLastUpdateDate());
        childImportRejection1.setVitADose6Dt(childImportRejection.getVitADose6Dt());
        childImportRejection1.setRemarks(childImportRejection.getRemarks());
        childImportRejection1.setaNMID(childImportRejection.getaNMID());
        childImportRejection1.setCreatedBy(childImportRejection.getCreatedBy());
        childImportRejection1.setUpdatedBy(childImportRejection.getUpdatedBy());
        childImportRejection1.setMeasles2Dt(childImportRejection.getMeasles2Dt());
        childImportRejection1.setWeightOfChild(childImportRejection.getWeightOfChild());
        childImportRejection1.setChildAadhaarNo(childImportRejection.getChildAadhaarNo());
        childImportRejection1.setChildEID(childImportRejection.getChildEID());
        childImportRejection1.setSex(childImportRejection.getSex());
        childImportRejection1.setVitADose5Dt(childImportRejection.getVitADose5Dt());
        childImportRejection1.setVitADose7Dt(childImportRejection.getVitADose7Dt());
        childImportRejection1.setVitADose8Dt(childImportRejection.getVitADose8Dt());
        childImportRejection1.setChildEIDTime(childImportRejection.getChildEIDTime());
        childImportRejection1.setFatherName(childImportRejection.getFatherName());
        childImportRejection1.setExecDate(childImportRejection.getExecDate());
        childImportRejection1.setAccepted(childImportRejection.getAccepted());
        childImportRejection1.setRejectionReason(childImportRejection.getRejectionReason());
        childImportRejection1.setBirthCertificateNumber(childImportRejection.getBirthCertificateNumber());
        childImportRejection1.setEntryType(childImportRejection.getEntryType());
        childImportRejection1.setSource(childImportRejection.getSource());
        childImportRejection1.setRegistrationNo(childImportRejection.getRegistrationNo());
        childImportRejection1.setmCTSMotherIDNo(childImportRejection.getmCTSMotherIDNo());
        childImportRejection1.setAction(childImportRejection.getAction());
    }

}
