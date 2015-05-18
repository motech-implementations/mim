package org.motechproject.nms.flw.repository;

import org.motechproject.mds.annotations.Lookup;
import org.motechproject.mds.annotations.LookupField;
import org.motechproject.mds.service.MotechDataService;
import org.motechproject.nms.flw.domain.ServiceUsage;
import org.motechproject.nms.props.domain.Service;

import java.util.List;

public interface ServiceUsageDataService extends MotechDataService<ServiceUsage> {
    @Lookup
    List<ServiceUsage> findByService(@LookupField(name = "service") Service service);
}
