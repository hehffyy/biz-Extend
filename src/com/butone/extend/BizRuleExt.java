package com.butone.extend;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.butone.extend.memorytable.DelegationManage;
import com.butone.extend.memorytable.PropertyModel;
import com.butone.extend.memorytable.TypeModel;
import com.butone.logic.config.CalcLogicConfig;
import com.butone.logic.impl.BizExprHelper;
import com.butone.model.BizRule;
import com.butone.model.BizRuleAction;
import com.butone.model.FieldDef;
import com.butone.model.TableDef;
import com.butone.xml.JaxbUtils;
import com.justep.util.Utils;

public class BizRuleExt {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4795298649375392268L;

	private BizRule bizRule;

	public BizRule getBizRule() {
		return bizRule;
	}

	public void setBizRule(BizRule bizRule) {
		this.bizRule = bizRule;
	}

	private List<BizRuleActionExt> actions;
	private List<TableDef> tempTableDefs;
	private CalcLogicConfig verifyLogicConfig;
	private Set<String> dependBizTables;

	public BizRuleExt(BizRule bizRule) {
		this.bizRule = bizRule;
		init();
	}

	public CalcLogicConfig getVerifyLogicConfig() {
		return verifyLogicConfig;
	}

	public void init() {
		verifyLogicConfig = null;
		if (bizRule.getVerifyLogic() != null) {
			try {
				verifyLogicConfig = (CalcLogicConfig) JaxbUtils.unMarshal(new ByteArrayInputStream(bizRule.getVerifyLogic().getBytes("utf-8")),
						"utf-8", CalcLogicConfig.class);
				verifyLogicConfig.prepare();
				bizRule.setVerifyLogic(null);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		actions = new ArrayList<BizRuleActionExt>();
		for (BizRuleAction action : bizRule.getActions()) {
			actions.add(new BizRuleActionExt(action));
		}
		createTempTableDef();
		parseDependBizTables();
	}

	private void parseDependBizTables() {
		dependBizTables = new HashSet<String>();
		if (Utils.isNotEmptyString(bizRule.getVerifyExpr())) {
			Set<String>  tables = BizExprHelper.parseObjectIdOfTableFunction(bizRule.getVerifyExpr());
			//删除临时表
			for(TableDef table : tempTableDefs){
				tables.remove(table.getName());
			}
			
			dependBizTables.addAll(tables);
		}
		if (Utils.isNotEmptyString(bizRule.getRelBizDatas())) {
			dependBizTables.addAll(Arrays.asList(bizRule.getRelBizDatas().split(",")));
		}
	}

	public Set<String> getDependBizTables() {
		return dependBizTables;
	}

	public List<BizRuleActionExt> getActionExts() {
		return actions;
	}

	private void createTempTableDef() {
		tempTableDefs = new ArrayList<TableDef>();
		if (bizRule.getDataModels() != null && !bizRule.getDataModels().trim().isEmpty()) {
			DelegationManage delegationmanage = new DelegationManage(bizRule.getDataModels());
			Map<String, ArrayList<TypeModel>> typeMap = delegationmanage.getXmlToBean();
			for (Entry<String, ArrayList<TypeModel>> entry : typeMap.entrySet()) {

				ArrayList<TypeModel> list = entry.getValue();
				for (TypeModel typeModel : list) {
					TableDef tabledef = new TableDef();
					// 显示名称
					tabledef.setDispName(typeModel.getDelegation().getLabel());
					// 物理名称
					tabledef.setName(typeModel.getDelegation().getClassname());

					List<PropertyModel> propetrymodels = typeModel.getDelegation().getPropetrymodels();
					for (PropertyModel propertyModel : propetrymodels) {
						FieldDef fielddef = new FieldDef();
						fielddef.setName(propertyModel.getName());
						fielddef.setDispName(propertyModel.getLabel());
						fielddef.setDataType(propertyModel.getType());
						fielddef.setAutoFillDef(propertyModel.getDefaultValue());
						tabledef.getFields().add(fielddef);
					}
					tempTableDefs.add(tabledef);
				}
			}
		}

	}

	public List<TableDef> getTempTableDefs() {
		return tempTableDefs;
	}

}
