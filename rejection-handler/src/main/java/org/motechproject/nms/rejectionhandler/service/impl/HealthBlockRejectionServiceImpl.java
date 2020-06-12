package org.motechproject.nms.rejectionhandler.service.impl;

import org.motechproject.nms.rejectionhandler.domain.HealthBlockImportRejection;
import org.motechproject.nms.rejectionhandler.repository.HealthBlockRejectionDataService;
import org.motechproject.nms.rejectionhandler.service.HealthBlockRejectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("healthBlockRejectionService")
public class HealthBlockRejectionServiceImpl implements HealthBlockRejectionService {
    @Autowired
    private HealthBlockRejectionDataService healthBlockRejectionDataService;

    @Override
    public void saveRejectedHealthBlock(HealthBlockImportRejection healthBlockImportRejection) {
        if(   healthBlockRejectionDataService.findByUniqueCode(healthBlockImportRejection.getStateId(),healthBlockImportRejection.getHealthBlockCode()) == null){
            healthBlockRejectionDataService.create(healthBlockImportRejection);
        }
        else {
            healthBlockRejectionDataService.update(healthBlockImportRejection);
        }
    }

    @Override
    public void createRejectedHealthBlock(HealthBlockImportRejection healthBlockImportRejection) {
        healthBlockRejectionDataService.create(healthBlockImportRejection);
    }
}
