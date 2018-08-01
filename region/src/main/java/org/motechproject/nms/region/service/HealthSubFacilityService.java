package org.motechproject.nms.region.service;

import org.motechproject.nms.region.domain.HealthFacility;
import org.motechproject.nms.region.domain.HealthSubFacility;
import org.motechproject.nms.region.domain.Taluka;

import java.util.List;
import java.util.Map;

public interface HealthSubFacilityService {
    HealthSubFacility findByHealthFacilityAndCode(HealthFacility healthFacility, Long code);
    HealthSubFacility create(HealthSubFacility healthSubFacility);
    HealthSubFacility update(HealthSubFacility healthSubFacility);

    Long createUpdateHealthSubFacilities(List<Map<String, Object>> recordList, Map<String, Taluka> talukaHashMap, Map<String, HealthFacility> healthFacilityHashMap);

    Long createUpdateVillageHealthSubFacility(List<Map<String, Object>> recordList);
}
