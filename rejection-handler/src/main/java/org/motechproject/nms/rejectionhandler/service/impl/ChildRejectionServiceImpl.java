package org.motechproject.nms.rejectionhandler.service.impl;

import org.motechproject.nms.rejectionhandler.domain.ChildImportRejection;
import org.motechproject.nms.rejectionhandler.repository.ChildRejectionDataService;
import org.motechproject.nms.rejectionhandler.service.ChildRejectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

import static org.motechproject.nms.tracking.utils.TrackChangeUtils.LOGGER;

/**
 * Created by beehyv on 20/7/17.
 */
@Service("childRejectionService")
public class ChildRejectionServiceImpl implements ChildRejectionService {

    @Autowired
    private ChildRejectionDataService childRejectionDataService;

    @Override
    public ChildImportRejection findByChildId(String idNo, String registrationNo) {
        return childRejectionDataService.findRejectedChild(idNo, registrationNo);
    }

    @Override
    public void createOrUpdateChild(ChildImportRejection childImportRejection) {
        ChildImportRejection childImportRejection1 = childRejectionDataService.findRejectedChild(childImportRejection.getIdNo(), childImportRejection.getRegistrationNo());

        if (childImportRejection1 == null && !childImportRejection.getAccepted()) {
            childImportRejection.setAction("CREATED");
            childImportRejection.setCreationDate(new Date());
            childRejectionDataService.create(childImportRejection);
        } else if (childImportRejection1 == null && childImportRejection.getAccepted()) {
            LOGGER.debug(String.format("There is no mother rejection data for mctsId %s and rchId %s", childImportRejection.getIdNo(), childImportRejection.getRegistrationNo()));
        } else if (childImportRejection1 != null && !childImportRejection1.getAccepted()) {
            childImportRejection.setModificationDate(new Date());
            childRejectionDataService.update(childImportRejection);
        } else if (childImportRejection1 != null && childImportRejection1.getAccepted()) {
            childImportRejection.setModificationDate(new Date());
            childImportRejection.setAction("UPDATED");
            childRejectionDataService.update(childImportRejection);
        }
    }
}