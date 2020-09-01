/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 * Contributors:                                                              *
 * Colin Rooney (croo) Patch 1605368 Fixed Payment Terms & Only due           *
 * Jorge Colmenarez (Frontuari, C.A.) Feature on Process PaySelection         *
 *****************************************************************************/
package net.frontuari.payselection.webui.apps.form;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.logging.Level;

import org.adempiere.util.Callback;
import org.adempiere.webui.ClientInfo;
import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.apps.ProcessModalDialog;
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.Checkbox;
import org.adempiere.webui.component.Column;
import org.adempiere.webui.component.Columns;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.component.ListboxFactory;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.component.WListbox;
import org.adempiere.webui.editor.WDateEditor;
import org.adempiere.webui.event.DialogEvents;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.event.WTableModelEvent;
import org.adempiere.webui.event.WTableModelListener;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.CustomForm;
import org.adempiere.webui.session.SessionManager;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.adempiere.webui.window.FDialog;
import org.compiere.model.MDocType;
import org.compiere.model.MPaySelection;
import org.compiere.model.MProcess;
import org.compiere.model.MSysConfig;
import org.compiere.model.X_C_PaySelection;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.compiere.util.ValueNamePair;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.SuspendNotAllowedException;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Center;
import org.zkoss.zul.North;
import org.zkoss.zul.Separator;
import org.zkoss.zul.South;
import org.zkoss.zul.Space;

/**
 *  Create Manual Payments From (AP) Invoices, (AR) Credit Memos, (PO) Purchased Order with Prepayment.
 *  Allows user to select Invoices or Order Purchased for payment.
 *  When Processed, PaySelection is created
 *  and optionally posted/generated and printed
 *
 *  @author Jorge Colmenarez
 *  @version $Id: WFTUPaySelect.java,v 1.0 2020/03/14 11:04:28 jlctmaster Frontuari, C.A. $
 */

public class WFTUPaySelect extends FTUPaySelect implements ValueChangeListener, WTableModelListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2935571591857121996L;

/** @todo withholding */
	
	private CustomForm form = new CustomForm();

	//
	private Panel mainPanel = new Panel();
	private Borderlayout mainLayout = new Borderlayout();
	private Panel parameterPanel = new Panel();
	private Label labelBankAccount = new Label();
	private Listbox fieldBankAccount = ListboxFactory.newDropdownListbox();
	private Grid parameterLayout = GridFactory.newGridLayout();
	private Label labelBankBalance = new Label();
	private Label labelCurrency = new Label();
	private Label labelBalance = new Label();
	private Checkbox onlyDue = new Checkbox();
	private Checkbox onlyPositiveBalance = new Checkbox();
	private Checkbox prePayment = new Checkbox();
	private Label labelBPartner = new Label();
	private Listbox fieldBPartner = ListboxFactory.newDropdownListbox();
	private Label dataStatus = new Label();
	private WListbox miniTable = ListboxFactory.newDataTable();
	private ConfirmPanel commandPanel = new ConfirmPanel(true, false, false, false, false, false, false);
	private Button bCancel = commandPanel.getButton(ConfirmPanel.A_CANCEL);
	private Button bGenerate = commandPanel.createButton(ConfirmPanel.A_PROCESS);
	private Button bRefresh = commandPanel.createButton(ConfirmPanel.A_REFRESH);
	private Label labelPayDate = new Label();
	private WDateEditor fieldPayDate = new WDateEditor();
	private Label labelPaymentRule = new Label();
	private Listbox fieldPaymentRule = ListboxFactory.newDropdownListbox();
	private Label labelDtype = new Label();
	private Listbox fieldDtype = ListboxFactory.newDropdownListbox();
	private Label labelDtypeTarget = new Label();
	private Listbox fieldDtypeTarget = ListboxFactory.newDropdownListbox();
	private Panel southPanel;
	private Checkbox chkOnePaymentPerInv = new Checkbox();
	private boolean m_isLock;
	//	Added by Jorge Colmenarez 2020-08-19 support for filter Org
	private Label organizationLabel = new Label();
	private Listbox organizationPick = ListboxFactory.newDropdownListbox();
	//	End Jorge Colmenarez
	
	/**
	 *	Initialize Panel
	 */
	public WFTUPaySelect()
	{
		try
		{
			m_WindowNo = form.getWindowNo();
			
			zkInit();
			dynInit();
			
			loadBankInfo();
			southPanel.appendChild(new Separator());
			southPanel.appendChild(commandPanel);
		}
		catch(Exception e)
		{
			log.log(Level.SEVERE, "", e);
		}
	}	//	init

	/**
	 *  Static Init
	 *  @throws Exception
	 */
	private void zkInit() throws Exception
	{
		//
		form.appendChild(mainPanel);
		mainPanel.appendChild(mainLayout);
		mainPanel.setStyle("width: 100%; height: 100%; padding: 0; margin: 0");
		ZKUpdateUtil.setHeight(mainLayout, "100%");
		ZKUpdateUtil.setWidth(mainLayout, "99%");
		parameterPanel.appendChild(parameterLayout);
		//
		labelBankAccount.setText(Msg.translate(Env.getCtx(), "C_BankAccount_ID"));
		fieldBankAccount.addActionListener(this);
		ZKUpdateUtil.setHflex(fieldBankAccount, "1");
		labelBPartner.setText(Msg.translate(Env.getCtx(), "C_BPartner_ID"));
		fieldBPartner.addActionListener(this);
		bRefresh.addActionListener(this);
		labelPayDate.setText(Msg.translate(Env.getCtx(), "PayDate"));
		labelPaymentRule.setText(Msg.translate(Env.getCtx(), "PaymentRule"));
		fieldPaymentRule.addActionListener(this);
		labelDtype.setText(Msg.translate(Env.getCtx(), "C_DocType_ID"));
		fieldDtype.addActionListener(this);
		ZKUpdateUtil.setHflex(fieldDtype, "1");
		//
		labelBankBalance.setText(Msg.translate(Env.getCtx(), "CurrentBalance"));
		labelBalance.setText("0");
		labelDtypeTarget.setText(Msg.translate(Env.getCtx(), "C_DocTypeTarget_ID"));
		fieldDtypeTarget.addActionListener(this);
		ZKUpdateUtil.setHflex(fieldDtypeTarget, "1");
		prePayment.setText(Msg.translate(Env.getCtx(), "IsOrderPrePayment"));
		prePayment.addActionListener(this);
		prePayment.setEnabled(false);
		onlyDue.setText(Msg.getMsg(Env.getCtx(), "OnlyDue"));
		dataStatus.setText(" ");
		dataStatus.setPre(true);
		onlyDue.addActionListener(this);
		fieldPayDate.addValueChangeListener(this);
		ZKUpdateUtil.setHflex(fieldPayDate.getComponent(), "1");
		
		chkOnePaymentPerInv.setText(Msg.translate(Env.getCtx(), MPaySelection.COLUMNNAME_IsOnePaymentPerInvoice));
		chkOnePaymentPerInv.addActionListener(this);

		onlyPositiveBalance.setText(Msg.getMsg(Env.getCtx(), "PositiveBalance"));
		onlyPositiveBalance.addActionListener(this);
		onlyPositiveBalance.setChecked(true);
		
		//	Added By Jorge Colmenarez 2020-08-19 09:24
		organizationLabel.setText(Msg.translate(Env.getCtx(), "AD_Org_ID"));
		
		//IDEMPIERE-2657, pritesh shah
		bGenerate.setEnabled(false);
		bGenerate.addActionListener(this);
		bCancel.addActionListener(this);
		//
		North north = new North();
		north.setStyle("border: none; max-height: 60%;");
		mainLayout.appendChild(north);
		north.appendChild(parameterPanel);		
		north.setSplittable(true);
		north.setCollapsible(true);
		north.setAutoscroll(true);
		LayoutUtils.addSlideSclass(north);
		
		if (ClientInfo.maxWidth(ClientInfo.MEDIUM_WIDTH-1))
		{
			Columns cols = new Columns();
			parameterLayout.appendChild(cols);
			Column col = new Column();
			col.setHflex("min");
			cols.appendChild(col);
			col = new Column();
			col.setHflex("1");
			cols.appendChild(col);
			col = new Column();
			col.setHflex("min");
			cols.appendChild(col);
			if (ClientInfo.minWidth(ClientInfo.SMALL_WIDTH))
			{
				col = new Column();
				col.setWidth("20%");
				cols.appendChild(col);
			}
		}
		
		Rows rows = parameterLayout.newRows();
		Row row = rows.newRow();
		row.appendChild(organizationLabel.rightAlign());
		//ZKUpdateUtil.setHflex(organizationPick.getComponent(), "true");
		row.appendChild(organizationPick);
		row = rows.newRow();
		row.appendChild(labelBankAccount.rightAlign());
		row.appendChild(fieldBankAccount);
		if (ClientInfo.maxWidth(ClientInfo.MEDIUM_WIDTH-1))
		{			
			row.appendChild(new Space());
			row = rows.newRow();
		}
		//	Add Document Type Target for PaySelection
		row.appendChild(labelDtypeTarget.rightAlign());
		row.appendChild(fieldDtypeTarget);
		if (ClientInfo.maxWidth(ClientInfo.MEDIUM_WIDTH-1))
		{
			row.appendChild(new Space());
			row = rows.newRow();
		}
		//	End
		row.appendChild(labelBankBalance.rightAlign());
		Panel balancePanel = new Panel();
		balancePanel.appendChild(labelCurrency);
		balancePanel.appendChild(labelBalance);
		row.appendChild(balancePanel);
		row.appendChild(new Space());
		
		row = rows.newRow();
		row.appendChild(labelBPartner.rightAlign());
		row.appendChild(fieldBPartner);
		if (ClientInfo.maxWidth(ClientInfo.MEDIUM_WIDTH-1))
		{		
			row.appendChild(new Space());
			row = rows.newRow();
		}
		row.appendChild(new Space());		
		row.appendChild(onlyDue);
		row.appendCellChild(prePayment);
		row.appendChild(new Space());
		
		row = rows.newRow();
		row.appendChild(labelDtype.rightAlign());
		row.appendChild(fieldDtype);
		if (ClientInfo.maxWidth(ClientInfo.MEDIUM_WIDTH-1))
		{
			row.appendChild(new Space());
			row = rows.newRow();
		}
		row.appendChild(new Space());
		row.appendChild(onlyPositiveBalance);
		row.appendCellChild(chkOnePaymentPerInv);
		row.appendChild(new Space());
		
		row = rows.newRow();
		row.appendChild(labelPayDate.rightAlign());
		row.appendChild(fieldPayDate.getComponent());
		if (ClientInfo.maxWidth(ClientInfo.MEDIUM_WIDTH-1))
		{			
			row.appendChild(new Space());
			row = rows.newRow();
		}
		row.appendChild(labelPaymentRule.rightAlign());
		row.appendChild(fieldPaymentRule);
		row.appendChild(bRefresh);
		if (ClientInfo.minWidth(ClientInfo.SMALL_WIDTH))
			LayoutUtils.expandTo(parameterLayout, 4, true);

		South south = new South();
		south.setStyle("border: none");
		mainLayout.appendChild(south);
		southPanel = new Panel();
		southPanel.appendChild(dataStatus);
		south.appendChild(southPanel);
		Center center = new Center();
		mainLayout.appendChild(center);
		center.appendChild(miniTable);
		//
		commandPanel.addButton(bGenerate);
		commandPanel.getButton(ConfirmPanel.A_OK).setVisible(false);
	}   //  jbInit

	/**
	 *  Dynamic Init.
	 *  - Load Bank Info
	 *  - Load BPartner
	 *  - Init Table
	 */
	private void dynInit()
	{


		// Organization filter selection
		ArrayList<KeyNamePair> orgData = getOrganization();
		for(KeyNamePair pp : orgData)
			organizationPick.appendItem(pp.getName(), pp);
		organizationPick.setSelectedIndex(0);
		
		
		ArrayList<BankInfo> bankAccountData = getBankAccountData();
		for(BankInfo bi : bankAccountData)
			fieldBankAccount.appendItem(bi.toString(), bi);

		if (fieldBankAccount.getItemCount() == 0)
			FDialog.error(m_WindowNo, form, "VPaySelectNoBank");
		else
			fieldBankAccount.setSelectedIndex(0);
		
		ArrayList<KeyNamePair> bpartnerData = getBPartnerData();
		for(KeyNamePair pp : bpartnerData)
			fieldBPartner.appendItem(pp.getName(), pp);
		fieldBPartner.setSelectedIndex(0);

		ArrayList<KeyNamePair> docTypeData = getDocTypeData();
		for(KeyNamePair pp : docTypeData)
			fieldDtype.appendItem(pp.getName(), pp);
		
		//	Document Type Target
		ArrayList<KeyNamePair> docTypeTargetData = getDocTypeTargetData();
		for(KeyNamePair ppt : docTypeTargetData)
			fieldDtypeTarget.appendItem(ppt.getName(), ppt);
		
		if(fieldDtypeTarget.getItemCount() == 0)
			FDialog.error(m_WindowNo, form, "VPaySelectNoDocumentType");
		else
			fieldDtypeTarget.setSelectedIndex(0);
		//	End
		
		prepareTable(miniTable,false);
		
		miniTable.getModel().addTableModelListener(this);
		//
		fieldPayDate.setMandatory(true);
		fieldPayDate.setValue(new Timestamp(System.currentTimeMillis()));
	}   //  dynInit

	/**
	 *  Load Bank Info - Load Info from Bank Account and valid Documents (PaymentRule)
	 */
	private void loadBankInfo()
	{		
		if (fieldBankAccount.getItemCount() == 0)
			return;
		
		BankInfo bi = (BankInfo)fieldBankAccount.getSelectedItem().getValue();
		
		labelCurrency.setText(bi.Currency);
		labelBalance.setText(m_format.format(bi.Balance));

		//  PaymentRule
		fieldPaymentRule.removeAllItems();
		
		ArrayList<ValueNamePair> paymentRuleData = getPaymentRuleData(bi);
		for(ValueNamePair vp : paymentRuleData)
			fieldPaymentRule.appendItem(vp.getName(), vp);
		fieldPaymentRule.setSelectedIndex(0);
	}   //  loadBankInfo
	


	/**
	 *  Verify Pre-Payment - Check if Document Type Target it's Pre-Payment
	 *  @author Jorge Colmenarez <mailto:jcolmenarez@frontuari.net>, 2020-05-02 15:54
	 */
	private void verifyPrePayment()
	{		
		if (fieldDtypeTarget.getItemCount() == 0)
			return;

		KeyNamePair docType = (KeyNamePair) fieldDtypeTarget.getSelectedItem().getValue();
		
		MDocType dt = new MDocType(Env.getCtx(), docType.getKey(), null);
		prePayment.setChecked(dt.get_ValueAsBoolean("IsOrderPrePayment"));
		
	}   //  verifyPrePayment

	/**
	 *  Query and create TableInfo
	 */
	private void loadTableInfo()
	{
		Timestamp payDate = (Timestamp)fieldPayDate.getValue();
		
		//IDEMPIERE-2657, pritesh shah
		if(payDate == null){
			throw new WrongValueException(fieldPayDate.getComponent(), Msg.getMsg(Env.getCtx(), "FillMandatory") + labelPayDate.getValue());
		}
		miniTable.setColorCompare(payDate);
		if (log.isLoggable(Level.CONFIG)) log.config("PayDate=" + payDate);
		
		if (fieldBankAccount.getItemCount() == 0) {
			FDialog.error(m_WindowNo, form, "VPaySelectNoBank");
			return;
		}
			
		
		BankInfo bi = fieldBankAccount.getSelectedItem().getValue();
		
		ValueNamePair paymentRule = (ValueNamePair) fieldPaymentRule.getSelectedItem().getValue();
		KeyNamePair bpartner = (KeyNamePair) fieldBPartner.getSelectedItem().getValue();
		KeyNamePair docType = (KeyNamePair) fieldDtype.getSelectedItem().getValue();
		//	Added by Jorge Colmenarez, 2020-08-19 11:58 
		KeyNamePair org = (KeyNamePair) organizationPick.getSelectedItem().getValue();
		
		//	prepareMiniTable
		prepareTable(miniTable,prePayment.isSelected());
		//	loadTableInfo
		loadTableInfo(bi, payDate, paymentRule, onlyDue.isSelected(), onlyPositiveBalance.isSelected(), prePayment.isSelected(), bpartner, docType, org, miniTable);
		
		calculateSelection();
		if (ClientInfo.maxHeight(ClientInfo.MEDIUM_HEIGHT-1))
		{
			mainLayout.getNorth().setOpen(false);
			LayoutUtils.addSclass("slide", mainLayout.getNorth());
		}
	}   //  loadTableInfo

	/**
	 * 	Dispose
	 */
	public void dispose()
	{
		SessionManager.getAppDesktop().closeActiveWindow();
	}	//	dispose

	
	/**************************************************************************
	 *  ActionListener
	 *  @param e event
	 */
	public void onEvent (Event e)
	{
		//  Update Bank Info
		if (e.getTarget() == fieldBankAccount)
			loadBankInfo();

		//  Generate PaySelection
		else if (e.getTarget() == bGenerate)
		{
			generatePaySelect();
		}

		else if (e.getTarget() == bCancel)
			dispose();
		
		else if(e.getTarget() == fieldDtypeTarget)
		{
			verifyPrePayment();
			loadTableInfo();
		}
		//  Update Open Invoices
		else if (e.getTarget() == fieldBPartner || e.getTarget() == bRefresh || e.getTarget() == fieldDtype
				|| e.getTarget() == fieldPaymentRule || e.getTarget() == onlyDue || e.getTarget() == onlyPositiveBalance
				 || e.getTarget() == prePayment)
			loadTableInfo();

		else if (DialogEvents.ON_WINDOW_CLOSE.equals(e.getName())) {
			//  Ask to Open Print Form
			/*FDialog.ask(m_WindowNo, form, "VPaySelectPrint?", new Callback<Boolean>() {

				@Override
				public void onCallback(Boolean result) 
				{
					if (result)
					{
						//  Start PayPrint
						int AD_Form_ID = FORM_PAYMENT_PRINT_EXPORT;	//	Payment Print/Export
						ADForm form = SessionManager.getAppDesktop().openForm(AD_Form_ID);
						if (m_ps != null)
						{
							WPayPrint pp = (WPayPrint) form.getICustomForm();
							pp.setPaySelection(m_ps.getC_PaySelection_ID());
						}
					}
					
				}
			});*/
			AEnv.zoom(MPaySelection.Table_ID, m_ps.getC_PaySelection_ID());
		}
		else if (e.getTarget().equals(chkOnePaymentPerInv))
		{
			m_isOnePaymentPerInvoice = chkOnePaymentPerInv.isChecked();
		}
	}   //  actionPerformed

	@Override
	public void valueChange(ValueChangeEvent e) {
		if (e.getSource() == fieldPayDate)
			loadTableInfo();
	}

	/**
	 *  Table Model Listener
	 *  @param e event
	 */
	public void tableChanged(WTableModelEvent e)
	{
		if (e.getColumn() == 0)
			calculateSelection();
		else if(e.getColumn() == 12)
		{
			int currRow = e.getLastRow();
			BigDecimal dueAmt = (BigDecimal) miniTable.getValueAt(currRow, 11); // Column DueAmt
			BigDecimal payAmt = (BigDecimal) miniTable.getValueAt(currRow, 12); // Column PayAmt
			if(dueAmt == null)
				dueAmt = BigDecimal.ZERO;
			if(payAmt == null)
				payAmt = BigDecimal.ZERO;
			if(payAmt.compareTo(dueAmt)>0)
			{
				String msg = Msg.translate(Env.getCtx(),"AmountPay")+":["+payAmt+"] > "+Msg.translate(Env.getCtx(),"AmountDue")+":["+dueAmt+"]";
				FDialog.error(m_WindowNo, form, "Error", msg);
				miniTable.setValueAt(dueAmt, currRow, 12); // Set Column PayAmt with DueAmt
			}
			else
				calculateSelection();
		}
	}   //  valueChanged

	/**
	 *  Calculate selected rows.
	 *  - add up selected rows
	 */
	public void calculateSelection()
	{
		dataStatus.setText(calculateSelection(miniTable));
		//
		bGenerate.setEnabled(m_noSelected != 0);
	}   //  calculateSelection

	/**
	 *  Generate PaySelection
	 */
	private void generatePaySelect()
	{
		if (miniTable.getRowCount() == 0)
			return;
		miniTable.setSelectedIndices(new int[]{0});
		calculateSelection();
		if (m_noSelected == 0)
			return;
		
		//	Remove support for set from Org selected, change by org from account
		//KeyNamePair org = (KeyNamePair) organizationPick.getSelectedItem().getValue();
		//int AD_Org_ID = org.getKey();

		KeyNamePair docTypeTarget = (KeyNamePair) fieldDtypeTarget.getSelectedItem().getValue();
		int C_DocType_ID = docTypeTarget.getKey();
		
		String msg = generatePaySelect(miniTable, (ValueNamePair) fieldPaymentRule.getSelectedItem().getValue(), 
				new Timestamp(fieldPayDate.getComponent().getValue().getTime()), 
				(BankInfo)fieldBankAccount.getSelectedItem().getValue(),prePayment.isSelected(),C_DocType_ID);
		
		if(msg != null && msg.length() > 0)		
		{
			FDialog.error(m_WindowNo, form, "SaveError", msg);
			return;
		}

		loadTableInfo();
		if (MSysConfig.getBooleanValue(MSysConfig.PAYMENT_SELECTION_MANUAL_ASK_INVOKE_GENERATE, true, m_ps.getAD_Client_ID(), m_ps.getAD_Org_ID())) {
		  //  Ask to Post it
		  FDialog.ask(m_WindowNo, form, "VPaySelectGenerate?", new Callback<Boolean>() {

			@Override
			public void onCallback(Boolean result) 
			{
				if (result)
				{
				//  Prepare Process 
					MProcess prc = MProcess.get(Env.getCtx(), "616529db-f078-48ff-b66f-7081197ca163");
					int AD_Proces_ID = prc.get_ID();	//	FTU_PaySelection_CreatePayment

					//	Execute Process
					ProcessModalDialog dialog = new ProcessModalDialog(WFTUPaySelect.this, m_WindowNo, 
							AD_Proces_ID, X_C_PaySelection.Table_ID, m_ps.getC_PaySelection_ID(), false);
					if (dialog.isValid()) {
						try {
							//dialog.setWidth("500px");
							dialog.setVisible(true);
							dialog.setPage(form.getPage());
							dialog.doHighlighted();
							// Create instance parameters. Parameters you want to send to the process.
							ProcessInfoParameter piParam = new ProcessInfoParameter(MPaySelection.COLUMNNAME_IsOnePaymentPerInvoice, m_isOnePaymentPerInvoice, "", "", "");
							dialog.getProcessInfo().setParameter(new ProcessInfoParameter[] {piParam});
						} catch (SuspendNotAllowedException e) {
							log.log(Level.SEVERE, e.getLocalizedMessage(), e);
						}
					}
				}
				
			}
		  });				
		} else {
			AEnv.zoom(MPaySelection.Table_ID, m_ps.getC_PaySelection_ID());
		}
	}   //  generatePaySelect
	
	/**
	 *  Lock User Interface
	 *  Called from the Worker before processing
	 */
	public void lockUI (ProcessInfo pi)
	{
		if (m_isLock) return;
		m_isLock = true;
		Clients.showBusy(null);
	}   //  lockUI

	/**
	 *  Unlock User Interface.
	 *  Called from the Worker when processing is done
	 */
	public void unlockUI (ProcessInfo pi)
	{
		if (!m_isLock) return;
		m_isLock = false;
		Clients.clearBusy();	
		
		this.dispose();
	}

	public void executeASync(ProcessInfo pi) {
	}

	public boolean isUILocked() {
		return m_isLock;
	}

	public ADForm getForm() {
		return form;
	}

	public void statusUpdate(String message) {
	}

	public void ask(final String message, final Callback<Boolean> callback) {
		Executions.schedule(form.getDesktop(), new EventListener<Event>() {
			@Override
			public void onEvent(Event event) throws Exception {
				FDialog.ask(m_WindowNo, null, message, callback);
			}
		}, new Event("onAsk"));		
	}

	public void download(File file) {		
	}

	public void askForInput(final String message, final Callback<String> callback) {
		Executions.schedule(form.getDesktop(), new EventListener<Event>() {
			@Override
			public void onEvent(Event event) throws Exception {
				FDialog.askForInput(m_WindowNo, null, message, callback);
			}
		}, new Event("onAskForInput"));
	}
	
}
