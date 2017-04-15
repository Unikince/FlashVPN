package com.lody.virtual.client.hook.proxies.statusbar;

import com.lody.virtual.client.hook.base.BinderInvocationProxy;
import com.lody.virtual.client.hook.base.MethodProxy;
import com.lody.virtual.helper.utils.VLog;

import java.lang.reflect.Method;

import mirror.com.android.internal.statusbar.IStatusBarService;

/**
 * Created by guojia on 2016/12/18.
 */
public class StatusBarManagerStub extends BinderInvocationProxy{

    public StatusBarManagerStub() {
        super(IStatusBarService.Stub.TYPE, "statusbar");
    }

    @Override
    protected void onBindMethods() {
        super.onBindMethods();
        addMethodProxy(new Disable());
    }

    private static class Disable extends MethodProxy {
        @Override
        public String getMethodName() {
            return "disable";
        }

        @Override
        public Object call(Object who, Method method, Object... args) throws Throwable {
            VLog.logbug("StatusBarManagerStub", "Disable hooked");
            return null;
        }
    }
}
