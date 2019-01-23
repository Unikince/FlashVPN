package com.polestar.task;

import com.polestar.task.network.datamodels.User;

/**
 * Created by guojia on 2019/1/19.
 */

public interface IUserStatusListener {


    void onRegisterSuccess(User user);
    void onRegisterFailed(ADErrorCode errorCode);

    void onGeneralError(ADErrorCode code);
}
