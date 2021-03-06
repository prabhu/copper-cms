package com.pogeyan.cmis.data.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.CriteriaContainerImpl;
import org.mongodb.morphia.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pogeyan.cmis.api.data.services.MTypeManagerDAO;
import com.pogeyan.cmis.api.uri.expression.BinaryExpression;
import com.pogeyan.cmis.api.uri.expression.BinaryOperator;
import com.pogeyan.cmis.api.uri.expression.ExpressionVisitor;
import com.pogeyan.cmis.api.uri.expression.FilterExpression;
import com.pogeyan.cmis.api.uri.expression.LiteralExpression;
import com.pogeyan.cmis.api.uri.expression.MemberExpression;
import com.pogeyan.cmis.api.uri.expression.MethodExpression;
import com.pogeyan.cmis.api.uri.expression.MethodOperator;
import com.pogeyan.cmis.api.uri.expression.ObjectLiteral;
import com.pogeyan.cmis.api.uri.expression.OrderByExpression;
import com.pogeyan.cmis.api.uri.expression.OrderExpression;
import com.pogeyan.cmis.api.uri.expression.PropertyExpression;
import com.pogeyan.cmis.api.uri.expression.SortOrder;
import com.pogeyan.cmis.api.uri.expression.UnaryExpression;
import com.pogeyan.cmis.api.uri.expression.UnaryOperator;
import com.pogeyan.cmis.impl.services.CmisNavigationService;

public class MongoExpressionVisitor<T> implements ExpressionVisitor {
	private static final Logger LOG = LoggerFactory.getLogger(CmisNavigationService.class);
	private Query<T> query;
	private MTypeManagerDAO typeManager;

	public MongoExpressionVisitor(Query<T> query, MTypeManagerDAO typeManager) {
		this.query = query;
		this.typeManager = typeManager;
	}

	@Override
	public Object visitFilterExpression(FilterExpression filterExpression, String expressionString, Object expression) {
		if (expressionString != null) {
			LOG.debug("visitFilterExpression: {}", expressionString);
		}
		if (expression.getClass() == CriteriaContainerImpl.class) {
			this.query.and((Criteria) expression);
			return this.query;
		}
		return expression;
	}

	@Override
	public Object visitBinary(BinaryExpression binaryExpression, BinaryOperator operator, Object leftSide,
			Object rightSide) {
		if (binaryExpression != null) {
			LOG.debug("visitBinary: {}, leftSide: {}, rightSide: {}", binaryExpression, leftSide, rightSide);
		}
		if (leftSide instanceof BinaryExpression) {
			// If something is lower in the tree and is of the type AND or OR it
			// needs brackets to show the higher priority
			if (BinaryOperator.AND.equals(((BinaryExpression) binaryExpression).getOperator())
					|| BinaryOperator.OR.equals(((BinaryExpression) binaryExpression).getOperator())) {

			}
		} else if (leftSide instanceof PropertyExpression) {
			PropertyExpression leftOp = (PropertyExpression) leftSide;
			String rightSideValue = null;
			if (rightSide instanceof PropertyExpression) {
				PropertyExpression rightOp = (PropertyExpression) rightSide;
				rightSideValue = rightOp.getUriLiteral();
			} else if (rightSide instanceof LiteralExpression) {
				LiteralExpression rightOp = (LiteralExpression) rightSide;
				rightSideValue = rightOp.getUriLiteral();
			} else if (rightSide instanceof String) {
				if (((String) rightSide).startsWith("'") && ((String) rightSide).endsWith("'")) {
					rightSideValue = ((String) rightSide).replace("'", "");
				} else {
					rightSideValue = rightSide.toString();
				}
			}
			switch (operator) {
			case EQ:
				if (rightSideValue.equals("null")) {
					return this.query.criteria(getQueryName(leftOp.getUriLiteral())).equal(null);
				} else if (rightSideValue.isEmpty()) {
					return this.query.criteria(getQueryName(leftOp.getUriLiteral())).equal("");
				} else {
					return this.query.criteria(getQueryName(leftOp.getUriLiteral()))
							.equal(getPropertyValue(leftOp.getUriLiteral(), rightSideValue));
				}
			case NE:
				return this.query.criteria(getQueryName(leftOp.getUriLiteral()))
						.notEqual(getPropertyValue(leftOp.getUriLiteral(), rightSideValue));
			case GE:
				return this.query.criteria(getQueryName(leftOp.getUriLiteral()))
						.greaterThanOrEq(getPropertyValue(leftOp.getUriLiteral(), rightSideValue));
			case GT:
				return this.query.criteria(getQueryName(leftOp.getUriLiteral()))
						.greaterThan(getPropertyValue(leftOp.getUriLiteral(), rightSideValue));
			case LE:
				return this.query.criteria(getQueryName(leftOp.getUriLiteral()))
						.lessThanOrEq(getPropertyValue(leftOp.getUriLiteral(), rightSideValue));
			case LT:
				return this.query.criteria(getQueryName(leftOp.getUriLiteral()))
						.lessThan(getPropertyValue(leftOp.getUriLiteral(), rightSideValue));
			default:
				// Other operators are not supported for SQL Statements
				throw new UnsupportedOperationException("Unsupported operator: " + operator.toUriLiteral());
			}
		} else {
			ArrayList<Criteria> operands = new ArrayList<>();
			if (leftSide.getClass() == CriteriaContainerImpl.class) {
				operands.add((Criteria) leftSide);
			}
			if (rightSide.getClass() == CriteriaContainerImpl.class) {
				operands.add((Criteria) rightSide);
			}
			switch (operator) {
			case OR:
				this.query.or(operands.toArray(new Criteria[operands.size()]));
				break;
			case AND:
				this.query.and(operands.toArray(new Criteria[operands.size()]));
				break;
			default:
				// Other operators are not supported for SQL Statements
				throw new UnsupportedOperationException("Unsupported operator: " + operator.toUriLiteral());
			}
		}

		// return the binary statement
		return this.query;
	}

	@Override
	public Object visitOrderByExpression(OrderByExpression orderByExpression, String expressionString,
			List<Object> orders) {
		if (expressionString != null) {
			LOG.debug("visitOrderByExpression: {}", expressionString);
		}
		String orderByQuery = orders.stream().filter(t -> t != null).map(n -> n.toString())
				.collect(Collectors.joining(","));
		return this.query.order(orderByQuery);
	}

	public Object getNumberObjectValue(String value) {
		Object convertValue;
		try {
			convertValue = Integer.parseInt(value);
		} catch (Exception e) {
			if (Pattern.matches("([0-9]*)\\.([0-9]*)", value.toString())) {
				convertValue = Double.parseDouble(value);
			} else {
				convertValue = Long.parseLong(value);
			}
		}
		return convertValue;
	}

	public Object getStringObjectValue(String value) {
		Object convertValue;
		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
			convertValue = Boolean.parseBoolean(value);
		} else {
			try {
				convertValue = Integer.parseInt(value);
			} catch (Exception e) {
				if (Pattern.matches("([0-9]*)\\.([0-9]*)", value.toString())) {
					convertValue = Double.parseDouble(value);
				} else {
					convertValue = value;
				}
			}
		}
		return convertValue;
	}

	public Object getPropertyValue(String propId, String value) {
		if (this.typeManager != null) {
			if (propId.equals("cmis:changeType")) {
				return Integer.parseInt(value);
			}
			try {
				TypeDefinition typeDef = this.typeManager.getAllPropertyById(propId, null);
				PropertyDefinition<?> propDef = typeDef.getPropertyDefinitions().get(propId);
				if (propDef != null) {
					if (propDef.getPropertyType().equals(PropertyType.STRING)) {
						return value;
					} else if (propDef.getPropertyType().equals(PropertyType.BOOLEAN)) {
						return Boolean.parseBoolean(value);
					} else if (propDef.getPropertyType().equals(PropertyType.DECIMAL)) {
						return Double.parseDouble(value);
					} else if (propDef.getPropertyType().equals(PropertyType.DATETIME)) {
						return Long.parseLong(value);
					} else if (propDef.getPropertyType().equals(PropertyType.ID)) {
						return value;
					} else if (propDef.getPropertyType().equals(PropertyType.INTEGER)) {
						return Integer.parseInt(value);
					}
				}
			} catch (Exception e) {
				LOG.error("class name:{},method name:{}, exception: {}", "MongoExpressionVisitor", "getPropertyValue",
						e);
			}
		}
		return null;
	}

	@Override
	public Object visitOrder(OrderExpression orderExpression, Object filterResult, SortOrder sortOrder) {
		if (orderExpression != null) {
			LOG.debug("visitOrder: {}", orderExpression);
		}
		if (filterResult instanceof PropertyExpression) {
			PropertyExpression orderByProperty = (PropertyExpression) filterResult;
			String orderByLiteral = sortOrder == SortOrder.asc ? orderByProperty.getUriLiteral()
					: "-" + orderByProperty.getUriLiteral();
			// morphia maintains an internal sort representation
			return orderByLiteral;
		}

		return null;
	}

	@Override
	public Object visitLiteral(LiteralExpression literal, ObjectLiteral edmLiteral) {
		return edmLiteral.getLiteral();
	}

	@Override
	public Object visitMethod(MethodExpression methodExpression, MethodOperator method, List<Object> parameters) {
		if (methodExpression != null) {
			LOG.debug("visitMethod: {}", methodExpression);
		}
		if (parameters.size() != 2) {
			throw new UnsupportedOperationException("Unsupported parameters length, it should be 2");
		}

		PropertyExpression fieldOperand = (PropertyExpression) parameters.get(0);
		String fieldValue = parameters.get(1).toString();
		fieldValue = fieldValue.replaceAll("\'", "");
		switch (method) {
		case EXISTS:
			if (fieldValue.equalsIgnoreCase("true")) {
				return this.query.criteria(getQueryName(fieldOperand.getUriLiteral())).exists();
			} else {
				return this.query.criteria(getQueryName(fieldOperand.getUriLiteral())).doesNotExist();
			}
		case IN:
			return this.query.criteria(getQueryName(fieldOperand.getUriLiteral()))
					.in(Arrays.asList(fieldValue.trim().split(" ")));
		case STARTSWITH:
			Pattern sw_pattern = Pattern.compile("^" + fieldValue, Pattern.CASE_INSENSITIVE);
			return this.query.filter(getQueryName(fieldOperand.getUriLiteral()), sw_pattern);

		case ENDSWITH:
			Pattern ew_pattern = Pattern.compile(Pattern.quote(fieldValue) + "$", Pattern.CASE_INSENSITIVE);
			return this.query.filter(getQueryName(fieldOperand.getUriLiteral()), ew_pattern);

		case CONTAINS:
			Pattern iew_pattern = Pattern.compile(Pattern.quote(fieldValue), Pattern.CASE_INSENSITIVE);
			return this.query.filter(getQueryName(fieldOperand.getUriLiteral()), iew_pattern);

		default:
			// Other operators are not supported for SQL Statements
			throw new UnsupportedOperationException("Unsupported operator: " + method.toUriLiteral());
		}
	}

	@Override
	public Object visitMember(MemberExpression memberExpression, Object path, Object property) {
		return null;
	}

	@Override
	public Object visitProperty(PropertyExpression propertyExpression, String uriLiteral) {
		return propertyExpression;
	}

	@Override
	public Object visitUnary(UnaryExpression unaryExpression, UnaryOperator operator, Object operand) {
		return null;
	}

	private static String getQueryName(String name) {
		if (name.equalsIgnoreCase(PropertyIds.PATH) || name.equalsIgnoreCase(PropertyIds.DESCRIPTION)
				|| name.equalsIgnoreCase(PropertyIds.PARENT_ID)
				|| name.equalsIgnoreCase(PropertyIds.CONTENT_STREAM_LENGTH)
				|| name.equalsIgnoreCase(PropertyIds.CONTENT_STREAM_FILE_NAME)
				|| name.equalsIgnoreCase(PropertyIds.CONTENT_STREAM_MIME_TYPE)
				|| name.equalsIgnoreCase(PropertyIds.CHECKIN_COMMENT)
				|| name.equalsIgnoreCase(PropertyIds.VERSION_LABEL)
				|| name.equalsIgnoreCase(PropertyIds.IS_MAJOR_VERSION)
				|| name.equalsIgnoreCase(PropertyIds.IS_LATEST_VERSION)
				|| name.equalsIgnoreCase(PropertyIds.IS_LATEST_MAJOR_VERSION) || name.equalsIgnoreCase(PropertyIds.NAME)
				|| name.equalsIgnoreCase(PropertyIds.IS_PRIVATE_WORKING_COPY)
				|| name.equalsIgnoreCase(PropertyIds.CREATED_BY) || name.equalsIgnoreCase(PropertyIds.CONTENT_STREAM_ID)
				|| name.equalsIgnoreCase(PropertyIds.VERSION_SERIES_CHECKED_OUT_ID)
				|| name.equalsIgnoreCase(PropertyIds.VERSION_SERIES_ID)
				|| name.equalsIgnoreCase(PropertyIds.IS_VERSION_SERIES_CHECKED_OUT)
				|| name.equalsIgnoreCase(PropertyIds.IS_IMMUTABLE) || name.equalsIgnoreCase("cmis:modifiedBy")
				|| name.equalsIgnoreCase(PropertyIds.VERSION_SERIES_CHECKED_OUT_BY)) {
			return getFieldName(name);
		} else if (name.equalsIgnoreCase(PropertyIds.OBJECT_ID)) {
			return "id";
		} else if (name.equalsIgnoreCase(PropertyIds.SECONDARY_OBJECT_TYPE_IDS)) {
			return "secondaryTypeIds";
		} else if (name.equalsIgnoreCase(PropertyIds.OBJECT_TYPE_ID)) {
			return "typeId";
		} else if (name.equalsIgnoreCase(PropertyIds.LAST_MODIFIED_BY)) {
			return "modifiedBy";
		} else if (name.equalsIgnoreCase(PropertyIds.CREATION_DATE)) {
			return "createdAt";
		} else if (name.equalsIgnoreCase(PropertyIds.CHANGE_TOKEN)) {
			return "token";
		} else if (name.equalsIgnoreCase("cmis:changeType")) {
			return "token.changeType";
		} else if (name.equalsIgnoreCase(PropertyIds.LAST_MODIFICATION_DATE)) {
			return "modifiedAt";
		} else if (name.equalsIgnoreCase(PropertyIds.BASE_TYPE_ID)) {
			return "baseId";
		} else if (name.equalsIgnoreCase("id")) {
			return "id";
		} else if (name.equalsIgnoreCase("operator")) {
			return "operator";
		} else {
			return "properties." + name;
		}
	}

	private static String getFieldName(Object value) {
		String valueString = value.toString();
		String[] values = valueString.split(":");
		String stringValue = values[1].replaceAll("[-+.^:',{}]", "");
		return stringValue;
	}
}
