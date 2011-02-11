/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.richfaces.application.configuration;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.el.ExpressionFactory;
import javax.el.ValueExpression;
import javax.faces.context.FacesContext;

import org.richfaces.el.util.ELUtils;

import com.google.common.base.Strings;
import com.google.common.primitives.Primitives;

/**
 * @author Nick Belaevski
 * 
 */
public class ConfigurationServiceImpl implements ConfigurationService {

    private Map<Enum<?>, ValueExpressionHolder> itemsMap = new ConcurrentHashMap<Enum<?>, ValueExpressionHolder>();

    private final ConfigurationItem getConfigurationItemAnnotation(Enum<?> enumKey) {
        try {
            ConfigurationItem item = enumKey.getClass().getField(enumKey.name()).getAnnotation(ConfigurationItem.class);
            if (item != null) {
                return item;
            }
        } catch (Exception e) {
            throw new IllegalStateException(MessageFormat.format("Cannot read @ConfigurationItem annotation from {0}.{1} because of {2}", 
                enumKey.getClass().getName(), enumKey.name(), e.getMessage()));
        }

        throw new IllegalStateException(MessageFormat.format("Annotation @ConfigurationItem is not set at {0}.{1}", 
            enumKey.getClass().getName(), enumKey.name()));
    }
    
    private <T> T coerce(FacesContext context, Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        
        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }
        
        if (value instanceof String) {
            PropertyEditor editor = PropertyEditorManager.findEditor(targetType);
            if (editor == null && Primitives.isWrapperType(targetType)) {
                editor = PropertyEditorManager.findEditor(Primitives.unwrap(targetType));
            }
            
            if (editor != null) {
                
                editor.setAsText((String) value);
                return targetType.cast(editor.getValue());
            } else if (targetType.isEnum()) {
                return targetType.cast(Enum.valueOf((Class<Enum>) targetType, (String) value));
            }
        }
        
        throw new IllegalArgumentException(MessageFormat.format("Cannot convert {0} to object of {1} type", value, targetType.getName()));
    }
    
    protected ValueExpressionHolder createValueExpressionHolder(FacesContext context, Enum<?> key, Class<?> targetType) {
        ConfigurationItem annotation = getConfigurationItemAnnotation(key);

        ValueExpression expression = createValueExpression(context, annotation, targetType);
        
        Object defaultValue = null;
        
        if (expression == null || !expression.isLiteralText()) {
            String defaultValueString = annotation.defaultValue();
            if (!Strings.isNullOrEmpty(defaultValueString)) {
                defaultValue = coerce(context, defaultValueString, targetType);
            }
        }
        
        return new ValueExpressionHolder(expression, defaultValue);
    }
    
    private final ValueExpression createValueExpression(FacesContext context, ConfigurationItem annotation, Class<?> targetType) {
        ConfigurationItemSource source = annotation.source();
        
        if (source == ConfigurationItemSource.contextInitParameter) {
            for (String name : annotation.names()) {
                String value = (String) context.getExternalContext().getInitParameter(name);
                
                if (Strings.isNullOrEmpty(value)) {
                    continue;
                }
            
                if (!annotation.literal() && ELUtils.isValueReference(value)) {
                    ExpressionFactory expressionFactory = context.getApplication().getExpressionFactory();
                    
                    if (expressionFactory == null) {
                        throw new IllegalStateException("ExpressionFactory is null");
                    }
                    
                    return expressionFactory.createValueExpression(context.getELContext(), value, targetType);
                } else {
                    Object coercedValue = coerce(context, value, targetType);
                    if (coercedValue != null) {
                        return new ConstantValueExpression(coercedValue);
                    }
                }
            }
            
            return null;
        } else {
            throw new IllegalArgumentException(source.toString());
        }
    }
    
    protected <T> T getValue(FacesContext facesContext, Enum<?> key, Class<T> returnType) {
        ValueExpressionHolder holder = itemsMap.get(key);

        if (holder == null) {
            holder = createValueExpressionHolder(facesContext, key, returnType);
            itemsMap.put(key, holder);
        }
        
        return returnType.cast(holder.getValue(facesContext));
    }

    public Boolean getBooleanValue(FacesContext facesContext, Enum<?> key) {
        return getValue(facesContext, key, Boolean.class);
    }

    public Integer getIntValue(FacesContext facesContext, Enum<?> key) {
        return getValue(facesContext, key, Integer.class);
    }

    public Long getLongValue(FacesContext facesContext, Enum<?> key) {
        return getValue(facesContext, key, Long.class);
    }

    public String getStringValue(FacesContext facesContext, Enum<?> key) {
        return getValue(facesContext, key, String.class);
    }

    public <T extends Enum<T>> T getEnumValue(FacesContext facesContext, Enum<?> key, Class<T> enumClass) {
        return getValue(facesContext, key, enumClass);
    }
    
    public Object getValue(FacesContext facesContext, Enum<?> key) {
        return getValue(facesContext, key, Object.class);
    }
}