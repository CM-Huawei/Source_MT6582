package com.mediatek.systemui;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestUtils {
    private static final String TAG = "StatusBarTest_TestUtils";

    public static Object getProperty(Object owner, String fieldName) {
        Object property = null;
        try {
            Class ownerClass = owner.getClass();
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            property = field.get(owner);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException fieldName = " + fieldName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException fieldName = " + fieldName);
        } catch (NoSuchFieldException e) {
            getSuperClassProperty(owner, fieldName);
        }
        return property;
    }

    public static Object getSuperClassProperty(Object owner, String fieldName) {
        Object property = null;
        try {
            Class ownerClass = owner.getClass().getSuperclass();
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            property = field.get(owner);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException fieldName = " + fieldName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException fieldName = " + fieldName);
        } catch (NoSuchFieldException e) {
            getSuper2ClassProperty(owner, fieldName);
        }
        return property;
    }

    public static Object getSuper2ClassProperty(Object owner, String fieldName) {
        Object property = null;
        try {
            Class ownerClass = owner.getClass().getSuperclass();
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            property = field.get(owner);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException fieldName = " + fieldName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException fieldName = " + fieldName);
        } catch (NoSuchFieldException e) {
            Log.d(TAG, "getSuper2ClassProperty NoSuchFieldException fieldName = " + fieldName);
        }
        return property;
    }

    public static Object getStaticProperty(Class ownerClass, String fieldName) {
        Object property = null;
        try {
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            property = field.get(null);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException fieldName = " + fieldName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException fieldName = " + fieldName);
        } catch (NoSuchFieldException e) {
            Log.d(TAG, "NoSuchFieldException fieldName = " + fieldName);
        }
        return property;
    }

    public static void setProperty(Object owner, String fieldName, Object newValue) {
        try {
            Class ownerClass = owner.getClass();
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(owner, newValue);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException fieldName = " + fieldName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException fieldName = " + fieldName);
        } catch (NoSuchFieldException e) {
            setSuperClassProperty(owner, fieldName, newValue);            
        }
    }

    public static void setSuperClassProperty(Object owner, String fieldName, Object newValue) {
        try {
            Class ownerClass = owner.getClass().getSuperclass();
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(owner, newValue);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException fieldName = " + fieldName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException fieldName = " + fieldName);
        } catch (NoSuchFieldException e) {
            setSuper2ClassProperty(owner, fieldName, newValue);
        }
    }

    public static void setSuper2ClassProperty(Object owner, String fieldName, Object newValue) {
        try {
            Class ownerClass = owner.getClass().getSuperclass().getSuperclass();
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(owner, newValue);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException fieldName = " + fieldName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException fieldName = " + fieldName);
        } catch (NoSuchFieldException e) {
            Log.d(TAG, "setSuper2ClassProperty NoSuchFieldException fieldName = " + fieldName);
        }
    }

    public static Object newInstance(Class ownerClass, Class[] args1, Object[] args2) {
        Object obj = null;
        try {
            Constructor constructor = ownerClass.getDeclaredConstructor(args1);
            constructor.setAccessible(true);
            obj = constructor.newInstance(args2);
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "NoSuchMethodException");
        } catch (InstantiationException e) {
            Log.d(TAG, "InstantiationException");
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException");
        } catch (InvocationTargetException e) {
            Log.d(TAG, "InvocationTargetException");
            Log.d(TAG, "", e.getCause());
        }
        return obj;
    }

    public static Object invokeMethod(Object owner, String methodName, Class[] args1, Object[] args2) {
        Object obj = null;
        try {
            Class ownerClass = owner.getClass();
            Method method = ownerClass.getDeclaredMethod(methodName, args1);
            method.setAccessible(true);
            obj = method.invoke(owner, args2);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException methodName = " + methodName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException methodName = " + methodName);
        } catch (NoSuchMethodException e) {
            invokeSuperClassMethod(owner, methodName, args1, args2);
        } catch (InvocationTargetException e) {
            Log.d(TAG, "InvocationTargetException methodName = " + methodName);
            Log.d(TAG, "", e.getCause());
        }

        return obj;
    }

    public static Object invokeSuperClassMethod(Object owner, String methodName, Class[] args1, Object[] args2) {
        Object obj = null;
        try {
            Class ownerClass = owner.getClass().getSuperclass();
            Method method = ownerClass.getDeclaredMethod(methodName, args1);
            method.setAccessible(true);
            obj = method.invoke(owner, args2);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException methodName = " + methodName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException methodName = " + methodName);
        } catch (NoSuchMethodException e) {
            invokeSuper2ClassMethod(owner, methodName, args1, args2);
        } catch (InvocationTargetException e) {
            Log.d(TAG, "InvocationTargetException methodName = " + methodName);
        }
        return obj;
    }

    public static Object invokeSuper2ClassMethod(Object owner, String methodName, Class[] args1, Object[] args2) {
        Object obj = null;
        try {
            Class ownerClass = owner.getClass().getSuperclass().getSuperclass().getSuperclass();
            Method method = ownerClass.getDeclaredMethod(methodName, args1);
            method.setAccessible(true);
            obj = method.invoke(owner, args2);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException methodName = " + methodName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException methodName = " + methodName);
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "invokeSuper2ClassMethod NoSuchMethodException methodName = " + methodName);
        } catch (InvocationTargetException e) {
            Log.d(TAG, "InvocationTargetException methodName = " + methodName);
        }
        return obj;
    }

    public static Object invokeStaticMethod(Object owner, String methodName, Class[] args1, Object[] args2) {
        Object obj = null;
        try {
            Class ownerClass = owner.getClass();
            Method method = ownerClass.getDeclaredMethod(methodName, args1);
            method.setAccessible(true);
            obj = method.invoke(null, args2);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException methodName = " + methodName);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException methodName = " + methodName);
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "NoSuchMethodException methodName = " + methodName);
            invokeSuperClassMethod(owner, methodName,  args1, args2);            
        } catch (InvocationTargetException e) {
            Log.d(TAG, "InvocationTargetException methodName = " + methodName);
            Log.d(TAG, "", e.getCause());
        }
        return obj;
    }

    public static void sleepBy(long timemills) {
        try {
            Thread.sleep(timemills);
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException");
        }
    }
}
