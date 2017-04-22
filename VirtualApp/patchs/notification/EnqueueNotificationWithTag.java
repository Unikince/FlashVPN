package com.lody.virtual.client.hook.patchs.notification;

import android.app.Notification;
import android.os.Build;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.hook.base.Hook;
import com.lody.virtual.client.ipc.VNotificationManager;
import com.lody.virtual.helper.utils.ArrayUtils;
import com.lody.virtual.os.VUserHandle;

import java.lang.reflect.Method;

/**
 * @author Lody
 *         <p>
 *         >=4.0.3
 */
/* package */ class EnqueueNotificationWithTag extends Hook {

    @Override
    public String getName() {
        return "enqueueNotificationWithTag";
    }

    @Override
    public Object call(Object who, Method method, Object... args) throws Throwable {
        //15 enqueueNotificationWithTag(pkg, tag, id, notification, idOut);
        //16 enqueueNotificationWithTag(pkg, tag, id, notification, idOut);
        //17 enqueueNotificationWithTag(pkg, tag, id, notification, idOut, UserHandle.myUserId());
        //18 enqueueNotificationWithTag(pkg, mContext.getBasePackageName(), tag, id, notification, idOut, UserHandle.myUserId());
        //19 enqueueNotificationWithTag(pkg, mContext.getOpPackageName(), tag, id, notification, idOut, UserHandle.myUserId());
        //21 enqueueNotificationWithTag(pkg, mContext.getOpPackageName(), tag, id, notification, idOut, UserHandle.myUserId());
        //22 enqueueNotificationWithTag(pkg, mContext.getOpPackageName(), tag, id, notification, idOut, UserHandle.myUserId());
        //23 enqueueNotificationWithTag(pkg, mContext.getOpPackageName(), tag, id, notification, idOut, UserHandle.myUserId());
        //24 enqueueNotificationWithTag(pkg, mContext.getOpPackageName(), tag, id, notification, idOut, user.getIdentifier());
        //25 enqueueNotificationWithTag(pkg, mContext.getOpPackageName(), tag, id, notification, idOut, user.getIdentifier());
        String pkg = (String) args[0];
        if (!VirtualCore.get().getComponentDelegate().isNotificationEnabled(pkg)){
            return null;
        }
        int notificationIndex = ArrayUtils.indexOfFirst(args, Notification.class);
        int idIndex = ArrayUtils.indexOfFirst(args, Integer.class);
        int tagIndex = (Build.VERSION.SDK_INT >= 18 ? 2 : 1);
        int id = (int) args[idIndex];
//        int user = (Build.VERSION.SDK_INT>=17?((int)args[args.length-1]):0);
        String tag = (String) args[tagIndex];
        //先处理id，再处理tag
        id = VNotificationManager.get().dealNotificationId(id, pkg, tag, getAppUserId());
        tag= VNotificationManager.get().dealNotificationTag(id, pkg, tag, getAppUserId());
        args[idIndex] = id;
        args[tagIndex] = tag;
        //tag和id确定一个通知栏是否重复
        Notification notification = (Notification) args[notificationIndex];
        if (!VNotificationManager.get().dealNotification(id, notification, pkg)) {
            return 0;
        }
        VNotificationManager.get().addNotification(id, tag, pkg, getAppUserId());
        args[0] = getHostPkg();
        if (Build.VERSION.SDK_INT >= 18 && args[1] instanceof String) {
            args[1] = getHostPkg();
        }
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			if(args[args.length - 1] instanceof Integer) {
				int userId = (int) args[args.length - 1];
				if (userId == VUserHandle.USER_ALL) {
					userId = VUserHandle.myUserId();
				}
				args[args.length - 1] = userId;
			}
		}
        return method.invoke(who, args);
    }
}