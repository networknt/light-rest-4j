package com.networknt.openapi.parameter;

import java.util.Map;

import com.networknt.oas.model.Example;
import com.networknt.oas.model.MediaType;
import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;

public class PoJoParameter implements Parameter {
	private String in;
	private String style;
	private boolean explode;
	private Schema schema;
	private String name;

	public PoJoParameter() {
	}

	public PoJoParameter(String name, String in, String style, boolean explode, Schema schema) {
		this.name = name;
		this.in = in;
		this.style = style;
		this.explode = explode;
		this.schema = schema;
	}

	@Override
	public String getKey() {
		// TODO Auto-generated method stub
		return null;
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

	@Override
	public Map<String, MediaType> getContentMediaTypes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, MediaType> getContentMediaTypes(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasContentMediaTypes() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasContentMediaType(String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public MediaType getContentMediaType(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setContentMediaTypes(Map<String, MediaType> contentMediaTypes) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setContentMediaType(String name, MediaType contentMediaType) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeContentMediaType(String name) {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<String, Example> getExamples() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Example> getExamples(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasExamples() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasExample(String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Example getExample(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setExamples(Map<String, Example> examples) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setExample(String name, Example example) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeExample(String name) {
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
	public Schema getSchema() {
		return schema;
	}

	@Override
	public Schema getSchema(boolean elaborate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setSchema(Schema schema) {
		this.schema = schema;
	}

	@Override
	public Boolean getAllowReserved() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAllowReserved() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setAllowReserved(Boolean allowReserved) {
		// TODO Auto-generated method stub

	}

	@Override
	public Boolean getExplode() {
		return explode;
	}

	@Override
	public boolean isExplode() {
		return explode;
	}

	@Override
	public void setExplode(Boolean explode) {
		this.explode = explode;
	}

	@Override
	public String getStyle() {
		return style;
	}

	@Override
	public void setStyle(String style) {
		this.style = style;
	}

	@Override
	public Boolean getAllowEmptyValue() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAllowEmptyValue() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setAllowEmptyValue(Boolean allowEmptyValue) {
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
	public Boolean getRequired() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isRequired() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setRequired(Boolean required) {
		// TODO Auto-generated method stub

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
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getIn() {
		return in;
	}

	@Override
	public void setIn(String in) {
		this.in = in;
	}

}
