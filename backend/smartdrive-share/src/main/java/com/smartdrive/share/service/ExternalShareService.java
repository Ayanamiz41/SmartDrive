package com.smartdrive.share.service;

import com.smartdrive.common.dto.SessionShareDto;
import com.smartdrive.common.vo.ShareInfoVO;

public interface ExternalShareService {
    ShareInfoVO getShareInfoCommon(String shareId);
    SessionShareDto checkShareCode(String shareId, String code);
}
