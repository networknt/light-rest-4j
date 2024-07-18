package com.networknt.openapi.parameter;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.networknt.oas.model.Discriminator;
import com.networknt.oas.model.ExternalDocs;
import com.networknt.oas.model.Schema;
import com.networknt.oas.model.Xml;

public class PojoSchema implements Schema {
	private Map<String, Schema> properties = new HashMap<>();
	private String type;

	public PojoSchema() {
	}

	public PojoSchema(String type, Map<String, Schema> properties) {
		this.type = type;
		this.properties.putAll(properties);
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getTitle() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setTitle(String title) {
		// TODO Auto-generated method stub

	}

	@Override
	public Number getMultipleOf() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMultipleOf(Number multipleOf) {
		// TODO Auto-generated method stub

	}

	@Override
	public Number getMaximum() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMaximum(Number maximum) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getExclusiveMaximum() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isExclusiveMaximum() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setExclusiveMaximum(Boolean exclusiveMaximum) {
		// TODO Auto-generated method stub

	}

	@Override
	public Number getMinimum() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMinimum(Number minimum) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getExclusiveMinimum() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isExclusiveMinimum() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setExclusiveMinimum(Boolean exclusiveMinimum) {
		// TODO Auto-generated method stub

	}

	@Override
	public Integer getMaxLength() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMaxLength(Integer maxLength) {
		// TODO Auto-generated method stub

	}

	@Override
	public Integer getMinLength() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMinLength(Integer minLength) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getPattern() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setPattern(String pattern) {
		// TODO Auto-generated method stub

	}

	@Override
	public Integer getMaxItems() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMaxItems(Integer maxItems) {
		// TODO Auto-generated method stub

	}

	@Override
	public Integer getMinItems() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMinItems(Integer minItems) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getUniqueItems() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isUniqueItems() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setUniqueItems(Boolean uniqueItems) {
		// TODO Auto-generated method stub

	}

	@Override
	public Integer getMaxProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMaxProperties(Integer maxProperties) {
		// TODO Auto-generated method stub

	}

	@Override
	public Integer getMinProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMinProperties(Integer minProperties) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<String> getRequiredFields() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getRequiredFields(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasRequiredFields() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getRequiredField(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setRequiredFields(List<String> requiredFields) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setRequiredField(int index, String requiredField) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addRequiredField(String requiredField) {
		// TODO Auto-generated method stub

	}

	@Override
	public void insertRequiredField(int index, String requiredField) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeRequiredField(int index) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<Object> getEnums() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Object> getEnums(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasEnums() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object getEnum(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setEnums(List<Object> enums) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setEnum(int index, Object enumValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addEnum(Object enumValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public void insertEnum(int index, Object enumValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeEnum(int index) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public void setType(String type) {
		this.type = type;
	}

	@Override
	public List<Schema> getAllOfSchemas() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Schema> getAllOfSchemas(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasAllOfSchemas() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Schema getAllOfSchema(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setAllOfSchemas(List<Schema> allOfSchemas) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setAllOfSchema(int index, Schema allOfSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addAllOfSchema(Schema allOfSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public void insertAllOfSchema(int index, Schema allOfSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeAllOfSchema(int index) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<Schema> getOneOfSchemas() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Schema> getOneOfSchemas(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasOneOfSchemas() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Schema getOneOfSchema(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setOneOfSchemas(List<Schema> oneOfSchemas) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setOneOfSchema(int index, Schema oneOfSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addOneOfSchema(Schema oneOfSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public void insertOneOfSchema(int index, Schema oneOfSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeOneOfSchema(int index) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<Schema> getAnyOfSchemas() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Schema> getAnyOfSchemas(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasAnyOfSchemas() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Schema getAnyOfSchema(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setAnyOfSchemas(List<Schema> anyOfSchemas) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setAnyOfSchema(int index, Schema anyOfSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addAnyOfSchema(Schema anyOfSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public void insertAnyOfSchema(int index, Schema anyOfSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeAnyOfSchema(int index) {
		// TODO Auto-generated method stub

	}

	@Override
	public Schema getNotSchema() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Schema getNotSchema(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setNotSchema(Schema notSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public Schema getItemsSchema() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Schema getItemsSchema(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setItemsSchema(Schema itemsSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<String, Schema> getProperties() {
		return properties;
	}

	@Override
	public Map<String, Schema> getProperties(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasProperties() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasProperty(String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Schema getProperty(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setProperties(Map<String, Schema> properties) {
		this.properties = properties;
	}

	@Override
	public void setProperty(String name, Schema property) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeProperty(String name) {
		// TODO Auto-generated method stub

	}

	@Override
	public Schema getAdditionalPropertiesSchema() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Schema getAdditionalPropertiesSchema(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setAdditionalPropertiesSchema(Schema additionalPropertiesSchema) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getAdditionalProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAdditionalProperties() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setAdditionalProperties(Boolean additionalProperties) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getUnevaluatedProperties() {
		return null;
	}

	@Override
	public boolean isUnevaluatedProperties() {
		return false;
	}

	@Override
	public void setUnevaluatedProperties(Boolean additionalProperties) {

	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDescription(String description) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getFormat() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setFormat(String format) {
		// TODO Auto-generated method stub

	}

	@Override
	public Object getDefault() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDefault(Object defaultValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getNullable() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isNullable() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setNullable(Boolean nullable) {
		// TODO Auto-generated method stub

	}

	@Override
	public Discriminator getDiscriminator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Discriminator getDiscriminator(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDiscriminator(Discriminator discriminator) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getReadOnly() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isReadOnly() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setReadOnly(Boolean readOnly) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getWriteOnly() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isWriteOnly() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setWriteOnly(Boolean writeOnly) {
		// TODO Auto-generated method stub

	}

	@Override
	public Xml getXml() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Xml getXml(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setXml(Xml xml) {
		// TODO Auto-generated method stub

	}

	@Override
	public ExternalDocs getExternalDocs() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ExternalDocs getExternalDocs(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setExternalDocs(ExternalDocs externalDocs) {
		// TODO Auto-generated method stub

	}

	@Override
	public Object getExample() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setExample(Object example) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getDeprecated() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isDeprecated() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setDeprecated(Boolean deprecated) {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<String, Object> getExtensions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getExtensions(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasExtensions() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasExtension(String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object getExtension(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setExtensions(Map<String, Object> extensions) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setExtension(String name, Object extension) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeExtension(String name) {
		// TODO Auto-generated method stub

	}

}
