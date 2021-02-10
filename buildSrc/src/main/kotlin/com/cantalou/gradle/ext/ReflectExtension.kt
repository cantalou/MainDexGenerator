package com.cantalou.gradle.ext

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 *
 * 反射工具类
 *
 * @author  LinZhiWei
 * @date    2020年04月03日 13:49
 *
 * Copyright (c) 2020年, 4399 Network CO.ltd. All Rights Reserved.
 */

fun Any.get(fieldName: String) : Any?{
    val field = findField(this.javaClass, fieldName)!!
    return field.get(this)
}

fun Any.set(fieldName: String, value: Any) {
    val field = findField(this.javaClass, fieldName)!!

    val modifierField = field.javaClass.getDeclaredField("modifiers")
    modifierField.isAccessible = true
    val modifier = modifierField.get(modifierField) as Int
    if((modifier and Modifier.FINAL) == Modifier.FINAL){
        modifierField.set(field, modifier and Modifier.FINAL.inv())
    }
    field.set(this, value)
}

private val fieldCache = HashMap<Int, Field>()

/**
 * Locates a given field anywhere in the class inheritance hierarchy.
 *
 * @param clazz an clazz to search the field into.
 * @param name  field name
 * @return a field object
 * @throws NoSuchFieldException if the field cannot be located
 */
@Throws(NoSuchFieldException::class)
fun findField(clazz_: Class<*>?, name: String): Field? {
    var clazz = clazz_
    while (clazz != null) {
        val key = clazz.hashCode() xor name.hashCode()
        var field: Field? = fieldCache[key]
        if (field != null && name == field.name) {
            return field
        }
        try {
            field = clazz.getDeclaredField(name)
        } catch (e: NoSuchFieldException) {
            // ignore and search next
        }

        // for sdk hide api
        if(field == null){
            try {
                field = clazz.getField(name)
            } catch (e: NoSuchFieldException) {
                // ignore and search next
            }
        }

        if (field != null) {
            if (!field.isAccessible) {
                field.isAccessible = true
            }
            fieldCache[key] = field
            return field
        }

        clazz = clazz.superclass
    }
    throw NoSuchFieldException("Field $name not found in $clazz and super class")
}