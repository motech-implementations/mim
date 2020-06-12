package org.motechproject.nms.rejectionhandler.service.impl;

import org.motechproject.nms.rejectionhandler.domain.TalukaHealthBlockImportRejection;
import org.motechproject.nms.rejectionhandler.repository.TalukaHealthBlockRejectionDataService;
import org.motechproject.nms.rejectionhandler.service.TalukaHealthBlockRejectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("talukaHealthBlockRejectionService")
public class TalukaHealthBlockRejectionServiceImpl implements TalukaHealthBlockRejectionService {
    @Autowired
    private TalukaHealthBlockRejectionDataService talukaHealthBlockRejectionDataService;

    @Override
    public void createRejectedTalukaHealthBlock(TalukaHealthBlockImportRejection talukaHealthBlockImportRejection) {
       talukaHealthBlockRejectionDataService.create(talukaHealthBlockImportRejection);
    }

    @Override
    public void saveRejectedTalukaHealthBlock(TalukaHealthBlockImportRejection talukaHealthBlockImportRejection) {
        if(   talukaHealthBlockRejectionDataService.findByUniqueCode(talukaHealthBlockImportRejection.getHealthBlockCode(),talukaHealthBlockImportRejection.getTalukaCode()) == null){
            talukaHealthBlockRejectionDataService.create(talukaHealthBlockImportRejection);
        }
        else {
            talukaHealthBlockRejectionDataService.update(talukaHealthBlockImportRejection);
        }
    }

}
