package com.scorpio.powerguard.mapper;

import com.scorpio.powerguard.entity.EmailSenderPool;
import java.util.List;

public interface EmailSenderPoolMapper {

    List<EmailSenderPool> selectEnabledSenders();
}
