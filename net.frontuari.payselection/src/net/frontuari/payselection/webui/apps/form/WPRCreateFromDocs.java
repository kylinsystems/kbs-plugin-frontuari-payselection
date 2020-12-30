package net.frontuari.payselection.webui.apps.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.ClientInfo;
import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.apps.form.WCreateFromWindow;
import org.adempiere.webui.component.Column;
import org.adempiere.webui.component.Columns;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListModelTable;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.component.ListboxFactory;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.editor.WEditor;
import org.adempiere.webui.editor.WSearchEditor;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.compiere.apps.IStatusBar;
import org.compiere.grid.CreateFrom;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;
import org.compiere.model.MBPBankAccount;
import org.compiere.model.MBPartner;
import org.compiere.model.MColumn;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MJournal;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MOrder;
import org.compiere.model.MRole;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zul.Space;

import net.frontuari.payselection.model.MFTUPaymentRequest;
import net.frontuari.payselection.model.MFTUPaymentRequestLine;
import net.frontuari.payselection.model.X_FTU_PaymentRequest;

public class WPRCreateFromDocs extends CreateFrom implements EventListener<Event>, ValueChangeListener {
	
	private WCreateFromWindow window;

	public WPRCreateFromDocs(GridTab tab) {
		super(tab);
		log.info(getGridTab().toString());
		
		window = new WCreateFromWindow(this, getGridTab().getWindowNo());
		
		p_WindowNo = getGridTab().getWindowNo();

		try
		{
			if (!dynInit())
				return;
			zkInit();
			setInitOK(true);
		}
		catch(Exception e)
		{
			log.log(Level.SEVERE, "", e);
			setInitOK(false);
		}
		AEnv.showWindow(window);
	}
	
	/** Window No               */
	private int p_WindowNo;

	/**	Logger			*/
	private static final CLogger log = CLogger.getCLogger(WPRCreateFromDocs.class);
	
	protected Label bPartnerLabel = new Label();
	protected WEditor bPartnerField;
	private Label labelDtype = new Label();
	private Listbox fieldDtype = ListboxFactory.newDropdownListbox();

	private Grid parameterStdLayout;

	private int noOfParameterColumn;
	
	//	Support for change BPartner Lookup for Search Field
	private int	m_C_BPartner_ID = 0;

	@Override
	public Object getWindow() {
		return window;
	}

	/**
	 *  Dynamic Init
	 *  @throws Exception if Lookups cannot be initialized
	 *  @return true if initialized
	 */
	public boolean dynInit() throws Exception
	{
		log.config("");
		
		window.setTitle(Msg.getElement(Env.getCtx(), "CreateFrom"));	
		
		initBPartner(true);
		bPartnerField.addValueChangeListener(this);
		
		ArrayList<KeyNamePair> docTypeData = getDocTypeData();
		for(KeyNamePair pp : docTypeData)
			fieldDtype.appendItem(pp.getName(), pp);
		
		return true;
	}   //  dynInit
	
	protected void zkInit() throws Exception
	{
		bPartnerLabel.setText(Msg.getElement(Env.getCtx(), "C_BPartner_ID"));
		labelDtype.setText(Msg.translate(Env.getCtx(), "C_DocType_ID"));
		fieldDtype.addActionListener(this);
		
    	Panel parameterPanel = window.getParameterPanel();
		
		parameterStdLayout = GridFactory.newGridLayout();
    	Panel parameterStdPanel = new Panel();
		parameterStdPanel.appendChild(parameterStdLayout);
		
		setupColumns(parameterStdLayout);

		parameterPanel.appendChild(parameterStdPanel);
		ZKUpdateUtil.setVflex(parameterStdLayout, "min");
		
		Rows rows = (Rows) parameterStdLayout.newRows();
		Row row = rows.newRow();
		//	Add Document Type by RequestType
		row.appendChild(labelDtype.rightAlign());
		row.appendChild(fieldDtype);
		if (ClientInfo.maxWidth(ClientInfo.MEDIUM_WIDTH-1))
		{			
			row.appendChild(new Space());
			row = rows.newRow();
		}
		row.appendChild(bPartnerLabel.rightAlign());
		if (bPartnerField != null)
			row.appendChild(bPartnerField.getComponent());
		if (ClientInfo.maxWidth(ClientInfo.MEDIUM_WIDTH-1))
		{
			row.appendChild(new Space());
			row = rows.newRow();
		}
		
        if (ClientInfo.isMobile()) {    		
    		if (noOfParameterColumn == 2)
				LayoutUtils.compactTo(parameterStdLayout, 2);		
			ClientInfo.onClientInfo(window, this::onClientInfo);
		}

        loadDocuments (0);
        
        hideEmptyRow(rows);
	}
	
	public void showWindow()
	{
		window.setVisible(true);
	}
	
	public void closeWindow()
	{
		window.dispose();
	}

	@Override
	public void info(IMiniTable miniTable, IStatusBar statusBar) {

	}

	protected void configureMiniTable (IMiniTable miniTable)
	{
		miniTable.setColumnClass(0, Boolean.class, false);      //  0-Selection
		miniTable.setColumnClass(1, String.class, true);   //  1-OrgName
		miniTable.setColumnClass(2, Timestamp.class, true);        //  2-DueDate
		miniTable.setColumnClass(3, String.class, true);        //  3-BPName
		miniTable.setColumnClass(4, String.class, true);        //  4-DocTypeName
		miniTable.setColumnClass(5, String.class, true);        //  5-DocumentNo
		miniTable.setColumnClass(6, String.class, true);        //  6-ISO_Code
		miniTable.setColumnClass(7, BigDecimal.class, true);	//  7-GrandTotal
		miniTable.setColumnClass(8, BigDecimal.class, true);	//  8-DueAmt
		miniTable.setColumnClass(9, BigDecimal.class, false);	//  9-PayAmt
		miniTable.setColumnClass(10, Boolean.class, false);      //  10-HasWithholding
		//  Table UI
		miniTable.autoSize();
	}
	
	protected Vector<String> getOISColumnNames()
	{
		//  Header Info
	    Vector<String> columnNames = new Vector<String>(9);
	    columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
	    columnNames.add(Msg.translate(Env.getCtx(), "AD_Org_ID"));
	    columnNames.add(Msg.translate(Env.getCtx(), "DueDate"));
	    columnNames.add(Msg.translate(Env.getCtx(), "C_BPartner_ID"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "C_DocType_ID"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "DocumentNo"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "C_Currency_ID"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "GrandTotal"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "DueAmt"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "PayAmt"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "IsTaxWithholding"));

	    return columnNames;
	}

	/**
	 *  Save - Create Payment Request Lines
	 *  @return true if saved
	 */
	public boolean save(IMiniTable miniTable, String trxName) {
		// Payment Request
		int FTU_PaymentRequest_ID = ((Integer)getGridTab().getValue("FTU_PaymentRequest_ID")).intValue();
		MFTUPaymentRequest pr = new MFTUPaymentRequest(Env.getCtx(), FTU_PaymentRequest_ID, trxName);
		if (log.isLoggable(Level.CONFIG)) log.config(pr.toString());
		//  Lines
		for (int i = 0; i < miniTable.getRowCount(); i++)
		{
			if (((Boolean)miniTable.getValueAt(i, 0)).booleanValue())
			{
				MFTUPaymentRequestLine line = new MFTUPaymentRequestLine(Env.getCtx(), 0, trxName);
				line.setFTU_PaymentRequest_ID(pr.get_ID());
				line.setAD_Org_ID(pr.getAD_Org_ID());
				
				//	Get Document
				KeyNamePair pp = (KeyNamePair)miniTable.getValueAt(i, 5);   //  5-DocumentNo
				int Record_ID = pp.getKey();
				//	BPartner
				MBPartner bp = null;
				//	For Invoices
				if(pr.getRequestType().equalsIgnoreCase(X_FTU_PaymentRequest.REQUESTTYPE_APInvoice)||pr.getRequestType().equalsIgnoreCase(MFTUPaymentRequest.REQUESTTYPE_ARInvoice))
				{
					line.setC_Invoice_ID(Record_ID);
					MInvoice doc = new MInvoice(Env.getCtx(), Record_ID, trxName);
					bp = new MBPartner(Env.getCtx(), doc.getC_BPartner_ID(), trxName);
				}
				//	For Order
				else if(pr.getRequestType().equalsIgnoreCase(X_FTU_PaymentRequest.REQUESTTYPE_PurchaseOrder))
				{
					line.setC_Order_ID(Record_ID);
					MOrder doc = new MOrder(Env.getCtx(), Record_ID, trxName);
					bp = new MBPartner(Env.getCtx(), doc.getC_BPartner_ID(), trxName);
				}
				else if (pr.getRequestType().equalsIgnoreCase(X_FTU_PaymentRequest.REQUESTTYPE_GLJournal)) {
					//line.set
					line.set_ValueOfColumn("GL_Journal_ID", Record_ID);
					MJournal GJorurnal = new MJournal(Env.getCtx(), Record_ID, trxName);
					bp = new MBPartner(Env.getCtx(), GJorurnal.get_ValueAsInt("C_BPartner_ID"), trxName);
				}
				else
					continue;
				
				line.setC_BPartner_ID(bp.getC_BPartner_ID());
				MBPBankAccount[] bpa = bp.getBankAccounts(true); 
				if(bpa.length <=0 )
					throw new AdempiereException("No hay cuentas de banco configuradas para el tercero:"+bp.getValue()+"-"+bp.getName());
				//	Set first row
				line.setC_BP_BankAccount_ID(bpa[0].getC_BP_BankAccount_ID());
				line.setDueDate((Timestamp)miniTable.getValueAt(i, 2));
				line.setPayAmt((BigDecimal)miniTable.getValueAt(i, 9));
				line.setIsPrepared(false);
				line.setProcessed(false);
				line.saveEx(trxName);
				
			}   //   if selected
		}   //  for all rows
		
		return false;
	}
	
	/**************************************************************************
	 *  Load BPartner Field
	 *  @param forInvoice true if Invoices are to be created, false receipts
	 *  @throws Exception if Lookups cannot be initialized
	 */
	protected void initBPartner (boolean forInvoice) throws Exception
	{
		String IsSOTrx = "N";
		String RequestType = getGridTab().get_ValueAsString("RequestType");
		if (RequestType.equals(MFTUPaymentRequest.REQUESTTYPE_ARInvoice))
			IsSOTrx="Y";
		StringBuffer where = new StringBuffer(" (EXISTS (SELECT 1 FROM C_Invoice i WHERE C_BPartner.C_BPartner_ID=i.C_BPartner_ID")
				  .append(" AND (i.IsSOTrx='"+IsSOTrx+"' OR (i.IsSOTrx='"+IsSOTrx+"' AND i.PaymentRule='D'))")
				  .append(" AND i.IsPaid<>'Y') OR EXISTS (SELECT 1 FROM C_Order o WHERE C_BPartner.C_BPartner_ID=o.C_BPartner_ID ")
				  .append(" AND o.IsSOTrx ='"+IsSOTrx+"' AND NOT EXISTS (SELECT 1 FROM C_Invoice inv WHERE inv.C_Order_ID = o.C_Order_ID))) ");
			
		
		//  load BPartner
		int AD_Column_ID = MColumn.getColumn_ID(MFTUPaymentRequestLine.Table_Name, MFTUPaymentRequestLine.COLUMNNAME_C_BPartner_ID) ;        //  C_Invoice.C_BPartner_ID
		MLookup lookup = MLookupFactory.get (Env.getCtx(), p_WindowNo, AD_Column_ID, 
				DisplayType.Search,Env.getLanguage(Env.getCtx()), "C_BPartner_ID"
				, 0, false, where.toString());
		bPartnerField = new WSearchEditor ("C_BPartner_ID", true, false, true, lookup);
		//
		//int C_BPartner_ID = Env.getContextAsInt(Env.getCtx(), p_WindowNo, "C_BPartner_ID");
		//bPartnerField.setValue(Integer.valueOf(C_BPartner_ID));
	}   //  initBPartner
	
	private void hideEmptyRow(org.zkoss.zul.Rows rows) {
		for(Component a : rows.getChildren()) {
			Row row = (Row) a;
			boolean visible = false;
			for(Component b : row.getChildren()) {
				if (b instanceof Space)
					continue;
				else if (!b.isVisible()) {
					continue;
				} else {
					if (!b.getChildren().isEmpty()) {
						for (Component c : b.getChildren()) {
							if (c.isVisible()) {
								visible = true;
								break;
							}
						}
					} else {
						visible = true;
						break;
					}
				}
			}
			row.setVisible(visible);
		}
	}
	
	protected void setupColumns(Grid parameterGrid) {
		noOfParameterColumn = ClientInfo.maxWidth((ClientInfo.EXTRA_SMALL_WIDTH+ClientInfo.SMALL_WIDTH)/2) ? 2 : 4;
		Columns columns = new Columns();
		parameterGrid.appendChild(columns);
		if (ClientInfo.maxWidth((ClientInfo.EXTRA_SMALL_WIDTH+ClientInfo.SMALL_WIDTH)/2))
		{
			Column column = new Column();
			ZKUpdateUtil.setWidth(column, "35%");
			columns.appendChild(column);
			column = new Column();
			ZKUpdateUtil.setWidth(column, "65%");
			columns.appendChild(column);
		}
		else
		{
			Column column = new Column();
			columns.appendChild(column);		
			column = new Column();
			ZKUpdateUtil.setWidth(column, "15%");
			columns.appendChild(column);
			ZKUpdateUtil.setWidth(column, "35%");
			column = new Column();
			ZKUpdateUtil.setWidth(column, "15%");
			columns.appendChild(column);
			column = new Column();
			ZKUpdateUtil.setWidth(column, "35%");
			columns.appendChild(column);
		}
	}
	
	protected void onClientInfo()
	{
		if (ClientInfo.isMobile() && parameterStdLayout != null && parameterStdLayout.getRows() != null)
		{
			int nc = ClientInfo.maxWidth((ClientInfo.EXTRA_SMALL_WIDTH+ClientInfo.SMALL_WIDTH)/2) ? 2 : 4;
			int cc = noOfParameterColumn;
			if (nc == cc)
				return;
			
			parameterStdLayout.getColumns().detach();
			setupColumns(parameterStdLayout);
			if (cc > nc)
			{
				LayoutUtils.compactTo(parameterStdLayout, nc);
			}
			else
			{
				LayoutUtils.expandTo(parameterStdLayout, nc, false);
			}
			hideEmptyRow(parameterStdLayout.getRows());
			
			ZKUpdateUtil.setCSSHeight(window);
			ZKUpdateUtil.setCSSWidth(window);
			window.invalidate();			
		}
	}

	/**
	 *  Change Listener
	 *  @param e event
	 */
	public void valueChange (ValueChangeEvent e)
	{
		if (log.isLoggable(Level.CONFIG)) log.config(e.getPropertyName() + "=" + e.getNewValue());

		//  BPartner - load Order/Invoice/Shipment
		if (e.getPropertyName().equals("C_BPartner_ID"))
		{
			Integer newBpValue = (Integer)e.getNewValue();
			int C_BPartner_ID = newBpValue == null?0:newBpValue.intValue();
			m_C_BPartner_ID = C_BPartner_ID;
			loadDocuments (C_BPartner_ID);
		}
		window.tableChanged(null);
	}   //  vetoableChange
	
	@Override
	public void onEvent(Event e) throws Exception {
		if(e.getTarget() == fieldDtype)
		{
			
			loadDocuments (m_C_BPartner_ID);
		}
	}
	
	/**
	 *  Load Order/Invoice/GLJournal data into Table
	 *  @param data data
	 */
	protected void loadTableOIS (Vector<?> data)
	{
		window.getWListbox().clear();
		
		//  Remove previous listeners
		window.getWListbox().getModel().removeTableModelListener(window);
		//  Set Model
		ListModelTable model = new ListModelTable(data);
		model.addTableModelListener(window);
		window.getWListbox().setData(model, getOISColumnNames());
		//
		
		configureMiniTable(window.getWListbox());
	}   //  loadOrder
	
	protected void loadDocuments (int C_BPartner_ID)
	{
		loadTableOIS(getDocumentData(C_BPartner_ID));
	}
	
	/**
	 *  Load Document Data 
	 *  @param C_BPartner_ID BPartner
	 *  @param RequestType 
	 */
	protected Vector<Vector<Object>> getDocumentData(int C_BPartner_ID)
	{
		if (log.isLoggable(Level.CONFIG)) log.config("C_BPartner_ID=" + C_BPartner_ID);
		
		KeyNamePair docType = (KeyNamePair) fieldDtype.getSelectedItem().getValue();
		
		MDocType dt = new MDocType(Env.getCtx(), docType.getKey(), null);

		String RequestType = getGridTab().get_ValueAsString("RequestType");
		int C_Currency_ID = (Integer)getGridTab().getValue("C_Currency_ID");
		int AD_Org_ID = (Integer)getGridTab().getValue("AD_Org_ID");
		Timestamp DateDoc = (Timestamp) getGridTab().getValue("DateDoc");
		StringBuilder groupBy = new StringBuilder(" ");
		//
		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		StringBuilder sql = new StringBuilder("SELECT ");
		if(RequestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_APInvoice))
		{
			sql.append(" i.C_Invoice_ID AS Record_ID, " //	1
					+ "o.Name AS OrgName,"	//	2
					+ "i.DueDate AS DateDue,"	// 3 
					+ "bp.Name AS BPName,"	// 4
					+ "dt.Name AS DocTypeName,"	// 5
					+ "i.DocumentNo,"	//	6
					+ "c.ISO_Code,"	//	7
					+ "i.GrandTotal,"	//	8
					+ "currencyConvert(invoiceOpen(i.C_Invoice_ID,i.C_InvoicePaySchedule_ID)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0),i.C_Currency_ID, ?"
					//Add Conversion By Negotiation Type By Argenis Rodríguez 09-12-2020
						+ ", CASE WHEN COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN ?"
						+ " ELSE i.DateInvoiced END"
					//End By Argenis Rodríguez
					+ ",i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountDue, "	//	9
					+ "currencyConvert(invoiceOpen(i.C_Invoice_ID,i.C_InvoicePaySchedule_ID)-invoiceDiscount(i.C_Invoice_ID,?,i.C_InvoicePaySchedule_ID)-invoiceWriteOff(i.C_Invoice_ID)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0),i.C_Currency_ID, ?"
					//Add Conversion By Negotiation Type By Argenis Rodríguez 09-12-2020
						+ ", CASE WHEN COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN ?"
						+ " ELSE i.DateInvoiced END"
					//End By Argenis Rodríguez
					+ ",i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountPay, "	// 10
					+ "COALESCE((SELECT MAX('Y') FROM LCO_InvoiceWithholding iw JOIN LVE_VoucherWithholding vw ON iw.LVE_VoucherWithholding_ID=vw.LVE_VoucherWithholding_ID"
					+ "	 WHERE iw.C_Invoice_ID=i.C_Invoice_ID AND vw.DocStatus IN ('CO','CL','BR')),'N') AS IsTaxWithholding") //11
					//	FROM
					.append(" FROM C_Invoice_v i"
					+ " INNER JOIN AD_Org o ON (i.AD_Org_ID=o.AD_Org_ID)"
					+ " INNER JOIN C_BPartner bp ON (i.C_BPartner_ID=bp.C_BPartner_ID)"
					+ " INNER JOIN C_DocType dt ON (i.C_DocType_ID=dt.C_DocType_ID)"
					+ " INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID)"
					+ " INNER JOIN C_PaymentTerm p ON (i.C_PaymentTerm_ID=p.C_PaymentTerm_ID)"
					+ " LEFT JOIN (SELECT psl.C_Invoice_ID,"
					+ "	SUM(currencyConvert(psl.PayAmt,cb.C_Currency_ID,i.C_Currency_ID,ps.PayDate,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM C_PaySelectionLine psl "
					+ "	INNER JOIN C_PaySelection ps on psl.C_PaySelection_ID = ps.C_PaySelection_ID "
					+ "	INNER JOIN C_BankAccount cb on ps.C_BankAccount_ID = cb.C_BankAccount_ID "
					+ " INNER JOIN C_Invoice i ON psl.C_Invoice_ID = i.C_Invoice_ID "
					+ " INNER JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID AND psc.C_Payment_ID IS NULL) "  
					+ " WHERE psl.IsActive='Y' "
					+ " GROUP BY psl.C_Invoice_ID) psl ON (i.C_Invoice_ID=psl.C_Invoice_ID) "
					+ " LEFT JOIN (SELECT prl.C_Invoice_ID,"
					+ "	SUM(currencyConvert(prl.PayAmt,pr.C_Currency_ID,i.C_Currency_ID,pr.DateDoc,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM FTU_PaymentRequest pr "
					+ " JOIN FTU_PaymentRequestLine prl on pr.FTU_PaymentRequest_ID = prl.FTU_PaymentRequest_ID "
					+ " JOIN C_Invoice i on prl.C_Invoice_ID = i.C_Invoice_ID "
					+ " WHERE pr.DocStatus NOT IN ('VO','RE') "
					+ " AND NOT EXISTS (SELECT 1 FROM C_PaySelectionLine psl WHERE prl.FTU_PaymentRequestLine_ID = psl.FTU_PaymentRequestLine_ID AND psl.IsActive='Y') "
					+ " GROUP BY prl.C_Invoice_ID) prl ON (i.C_Invoice_ID = prl.C_Invoice_ID) ")
					//	WHERE
					.append(" WHERE i.IsSOTrx='N' AND IsPaid='N'"
					+ " AND (invoiceOpen(i.C_Invoice_ID, i.C_InvoicePaySchedule_ID)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0)) > 0" //Check that AmountDue <> 0
					+ " AND i.DocStatus IN ('CO','CL')"
					+ "  AND i.AD_Client_ID=? AND i.AD_Org_ID=?"
					+ "  AND i.DateAcct <= ?");
		}
		else if (RequestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_PurchaseOrder))
		{
			sql.append(" i.C_Order_ID AS Record_ID, "	//	1
					+ "o.Name AS OrgName,"	//	2
					+ "i.DueDate AS DateDue,"	// 3 
					+ "bp.Name AS BPName,"	// 4
					+ "dt.Name AS DocTypeName,"	// 5
					+ "i.DocumentNo,"	//	6
					+ "c.ISO_Code,"	//	7
					+ "i.GrandTotal,"	//	8
					+ "currencyConvert(ftuOrderOpen(i.C_Order_ID,i.C_OrderPaySchedule_ID)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0) ,i.C_Currency_ID, ?"
					//Add Conversion By Negotiation Type By Argenis Rodríguez 09-12-2020
						+ ", CASE COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN ?"
						+ " ELSE i.DateOrdered END"
					//End By Argenis Rodríguez
					+ ",i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID)AS AmountDue, "	//	9
					+ "currencyConvert(ftuOrderOpen(i.C_Order_ID,i.C_OrderPaySchedule_ID)-ftuOrderDiscount(i.C_Order_ID,?,i.C_OrderPaySchedule_ID)-ftuOrderWriteOff(i.C_Order_ID)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0),i.C_Currency_ID, ?"
					//Add Conversion By Negotiation Type By Argenis Rodríguez 09-12-2020
						+ ", CASE COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN ?"
						+ " ELSE i.DateOrdered END"
					//End By Argenis Rodríguez
					+ ",i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountPay,"	//	10
					+ "'N' AS IsTaxWithholding")
					//	FROM
					.append(" FROM FTU_Order_v i"
					+ " INNER JOIN AD_Org o ON (i.AD_Org_ID=o.AD_Org_ID)"
					+ " INNER JOIN C_BPartner bp ON (i.C_BPartner_ID=bp.C_BPartner_ID)"
					+ " INNER JOIN C_DocType dt ON (i.C_DocType_ID=dt.C_DocType_ID)"
					+ " INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID)"
					+ "  INNER JOIN C_PaymentTerm p ON (i.C_PaymentTerm_ID=p.C_PaymentTerm_ID AND EXISTS (select 1 from C_PaySchedule ps where p.C_PaymentTerm_ID = ps.C_PaymentTerm_ID AND ps.IsActive = 'Y'))"
					+ " LEFT JOIN (SELECT psl.C_Order_ID,"
					+ "	SUM(currencyConvert(psl.PayAmt,cb.C_Currency_ID,i.C_Currency_ID,ps.PayDate,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM C_PaySelectionLine psl "
					+ "	INNER JOIN C_PaySelection ps on psl.C_PaySelection_ID = ps.C_PaySelection_ID "
					+ "	INNER JOIN C_BankAccount cb on ps.C_BankAccount_ID = cb.C_BankAccount_ID "
					+ " INNER JOIN C_Order i ON psl.C_Order_ID = i.C_Order_ID "
					+ " INNER JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID AND psc.C_Payment_ID IS NULL) "  
					+ " WHERE psl.IsActive='Y' "
					+ " GROUP BY psl.C_Order_ID) psl ON (i.C_Order_ID=psl.C_Order_ID) "
					+ " LEFT JOIN (SELECT prl.C_Order_ID,"
					+ "	SUM(currencyConvert(prl.PayAmt,pr.C_Currency_ID,i.C_Currency_ID,pr.DateDoc,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM FTU_PaymentRequest pr "
					+ " JOIN FTU_PaymentRequestLine prl on pr.FTU_PaymentRequest_ID = prl.FTU_PaymentRequest_ID "
					+ " JOIN C_Order i on prl.C_Order_ID = i.C_Order_ID "
					+ " WHERE pr.DocStatus NOT IN ('VO','RE') "
					+ " AND NOT EXISTS (SELECT 1 FROM C_PaySelectionLine psl WHERE prl.FTU_PaymentRequestLine_ID = psl.FTU_PaymentRequestLine_ID AND psl.IsActive='Y') "
					+ " GROUP BY prl.C_Order_ID) prl ON (i.C_Order_ID = prl.C_Order_ID) ")
					//	WHERE
					.append(" WHERE i.IsSOTrx='N' AND i.C_Invoice_ID IS NULL "
					+ " AND (ftuOrderOpen(i.C_Order_ID, i.C_OrderPaySchedule_ID)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0)) > 0" //Check that AmountDue <> 0
					+ " AND i.DocStatus IN ('CO','CL')"
					+ " AND i.AD_Client_ID=? AND i.AD_Org_ID=?");
		}else if (RequestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_GLJournal)) {

			int C_DocType_ID = (Integer)getGridTab().getValue("C_DocType_ID");
			MDocType DocType = new MDocType(Env.getCtx(),C_DocType_ID,null);
			int Account_ID = DocType.get_ValueAsInt("Account_ID");
			
			sql.append("i.GL_Journal_ID AS Record_ID," //1
					+ "o.Name as OrgName," //2
					+ "i.DateDoc AS DateDue,"	// 3 
					+ "bp.Name AS BPName,"	// 4
					+ "dt.Name AS DocTypeName,"	// 5
					+ "i.DocumentNo,"	//	6
					+ "c.ISO_Code,"	//	7
					+ "SUM(il.AmtSourceCr) AS GrandTotal,"	//	8
					+ "currencyConvert(SUM(il.AmtSourceCr),i.C_Currency_ID, ?"
					//Add Conversion By Negotiation Type By Argenis Rodríguez 09-12-2020
						+ ", CASE COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN ?"
						+ " ELSE i.DateDoc END"
					//End By Argenis Rodríguez
					+ ",i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountDue," //9
					+ "currencyConvert(SUM(il.AmtSourceCr),i.C_Currency_ID, ?"
					//Add Conversion By Negotiation Type By Argenis Rodríguez 09-12-2020
						+ ", CASE COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN ?"
						+ " ELSE i.DateDoc END"
					//End By Argenis Rodríguez
					+ ",i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountPay," //10
					+ "'N' AS IsTaxWithholding")//11
					// FROM 
					.append(" FROM GL_Journal i"
							+ " INNER JOIN GL_JournalLine il ON i.GL_Journal_ID=il.GL_Journal_ID"
							+ " INNER JOIN AD_Org o ON (i.AD_Org_ID=o.AD_Org_ID)"
							+ " INNER JOIN C_BPartner bp ON (i.C_BPartner_ID=bp.C_BPartner_ID)"
							+ " INNER JOIN C_DocType dt ON (i.C_DocType_ID=dt.C_DocType_ID)"
							+ " INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID)"
							+ " LEFT JOIN (SELECT prl.GL_Journal_ID,"
							+ "	SUM(currencyConvert(prl.PayAmt,pr.C_Currency_ID,i.C_Currency_ID,pr.DateDoc,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
							+ "	FROM FTU_PaymentRequest pr "
							+ " JOIN FTU_PaymentRequestLine prl on pr.FTU_PaymentRequest_ID = prl.FTU_PaymentRequest_ID "
							+ " JOIN GL_Journal i on prl.GL_Journal_ID = i.GL_Journal_ID "
							+ " WHERE pr.DocStatus NOT IN ('VO','RE') "
							//+ " AND NOT EXISTS (SELECT 1 FROM C_PaySelectionLine psl WHERE prl.FTU_PaymentRequestLine_ID = psl.FTU_PaymentRequestLine_ID) "
							+ " GROUP BY prl.GL_Journal_ID) prl ON (i.GL_Journal_ID = prl.GL_Journal_ID) "							
							//+ ""
							)
					// WHERE
					.append(" WHERE dt.IsRequiredPayment='Y' AND i.DocStatus IN ('CO','CL')"
							+ " AND i.AD_Client_ID=? AND i.AD_Org_ID=?"
							+ " AND il.Account_ID="+Account_ID);
					//GROUP
					groupBy.append(" GROUP BY i.GL_Journal_ID,o.Name,i.DateDoc,bp.Name,dt.Name,i.DocumentNo,c.ISO_Code");
		}else if (RequestType.equals(MFTUPaymentRequest.REQUESTTYPE_ARInvoice)) {
			sql.append(" i.C_Invoice_ID AS Record_ID, " //	1
					+ "o.Name AS OrgName,"	//	2
					+ "i.DueDate AS DateDue,"	// 3 
					+ "bp.Name AS BPName,"	// 4
					+ "dt.Name AS DocTypeName,"	// 5
					+ "i.DocumentNo,"	//	6
					+ "c.ISO_Code,"	//	7
					+ "i.GrandTotal,"	//	8
					/*+ "currencyConvert(invoiceOpen(i.C_Invoice_ID,i.C_InvoicePaySchedule_ID),i.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0) AS AmountDue, "	//	9
					+ "currencyConvert(invoiceOpen(i.C_Invoice_ID,i.C_InvoicePaySchedule_ID)-invoiceDiscount(i.C_Invoice_ID,?,i.C_InvoicePaySchedule_ID)-invoiceWriteOff(i.C_Invoice_ID),i.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0) AS AmountPay,"	// 10
					+ "COALESCE((SELECT 'Y' FROM LCO_InvoiceWithholding iw JOIN LVE_VoucherWithholding vw ON iw.LVE_VoucherWithholding_ID=vw.LVE_VoucherWithholding_ID"
					+ "	 WHERE iw.C_Invoice_ID=i.C_Invoice_ID AND vw.DocStatus IN ('CO','CL','BR')),'N') AS IsTaxWithholding") //11*/
					+ "currencyConvert(vw.withholdingAmt-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0),i.C_Currency_ID, ?"
					//Add Conversion By Negotiation Type By Argenis Rodríguez 09-12-2020
						+ ", CASE COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN ?"
						+ " ELSE i.DateInvoiced END"
					//End By Argenis Rodríguez
					+ ",i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountDue, "	//	9
					+ "currencyConvert(vw.withholdingAmt-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0),i.C_Currency_ID, ?"
					//Add Conversion By Negotiation Type By Argenis Rodríguez 09-12-2020
						+ ", CASE COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN ?"
						+ " ELSE i.DateInvoiced END"
					//End By Argenis Rodríguez
					+ ",i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountPay,"	// 10
					+ " CASE WHEN vw.C_Invoice_ID > 0 THEN 'Y' ELSE 'N' END AS IsTaxWithholding") //11
					//	FROM
					.append(" FROM C_Invoice_v i"
					+ " INNER JOIN AD_Org o ON (i.AD_Org_ID=o.AD_Org_ID)"
					+ " INNER JOIN C_BPartner bp ON (i.C_BPartner_ID=bp.C_BPartner_ID)"
					+ " INNER JOIN C_DocType dt ON (i.C_DocType_ID=dt.C_DocType_ID)"
					+ " INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID)"
					+ " INNER JOIN C_PaymentTerm p ON (i.C_PaymentTerm_ID=p.C_PaymentTerm_ID)"
					+ " LEFT JOIN (SELECT SUM(currencyConvert(iw.TaxAmt,vw.C_Currency_ID,winv.C_Currency_ID,vw.DateTrx,vw.C_ConversionType_ID,vw.AD_Client_ID,vw.AD_Org_ID)) as withholdingAmt,iw.C_Invoice_ID FROM LCO_InvoiceWithholding iw "
						+ " JOIN LVE_VoucherWithholding vw ON iw.LVE_VoucherWithholding_ID=vw.LVE_VoucherWithholding_ID"
						+ " JOIN C_Invoice winv ON iw.C_Invoice_ID=winv.C_Invoice_ID "
						+ " WHERE vw.DocStatus IN ('CO','CL','BR') GROUP BY iw.C_Invoice_ID) vw ON (vw.C_Invoice_ID=i.C_Invoice_ID)"
					+ " LEFT JOIN (SELECT psl.C_Invoice_ID,"
					+ "	SUM(currencyConvert(psl.PayAmt,cb.C_Currency_ID,i.C_Currency_ID,ps.PayDate,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM C_PaySelectionLine psl "
					+ "	INNER JOIN C_PaySelection ps on psl.C_PaySelection_ID = ps.C_PaySelection_ID "
					+ "	INNER JOIN C_BankAccount cb on ps.C_BankAccount_ID = cb.C_BankAccount_ID "
					+ " INNER JOIN C_Invoice i ON psl.C_Invoice_ID = i.C_Invoice_ID "
					+ " INNER JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID AND psc.C_Payment_ID IS NULL) "  
					+ " WHERE psl.IsActive='Y' "
					+ " GROUP BY psl.C_Invoice_ID) psl ON (i.C_Invoice_ID=psl.C_Invoice_ID) "
					+ " LEFT JOIN (SELECT prl.C_Invoice_ID,"
					+ "	SUM(currencyConvert(prl.PayAmt,pr.C_Currency_ID,i.C_Currency_ID,pr.DateDoc,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM FTU_PaymentRequest pr "
					+ " JOIN FTU_PaymentRequestLine prl on pr.FTU_PaymentRequest_ID = prl.FTU_PaymentRequest_ID "
					+ " JOIN C_Invoice i on prl.C_Invoice_ID = i.C_Invoice_ID "
					+ " WHERE pr.DocStatus NOT IN ('VO','RE') "
					+ " AND NOT EXISTS (SELECT 1 FROM C_PaySelectionLine psl WHERE prl.FTU_PaymentRequestLine_ID = psl.FTU_PaymentRequestLine_ID AND psl.IsActive='Y') "
					+ " GROUP BY prl.C_Invoice_ID) prl ON (i.C_Invoice_ID = prl.C_Invoice_ID) ")
					//	WHERE
					.append(" WHERE i.IsSOTrx='Y' AND (COALESCE(vw.withholdingAmt,0)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0)) > 0"
					//+ " AND (invoiceOpen(i.C_Invoice_ID, i.C_InvoicePaySchedule_ID)-COALESCE(psl.PayAmt,0)-COALESCE(prl.PayAmt,0)) > 0" //Check that AmountDue <> 0
					+ " AND i.DocStatus IN ('CO','CL')"
					+ "  AND i.AD_Client_ID=? AND i.AD_Org_ID=?"
					+ "  AND i.DateAcct <= ?");
		}
		if(C_BPartner_ID > 0)
		{
			sql.append(" AND i.C_BPartner_ID = "+C_BPartner_ID);
		}
		if(dt!=null && dt.get_ID()>0)
		{
			sql.append(" AND i.C_DocType_ID = "+dt.get_ID());
		}
		sql.append(groupBy);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			int index = 1;
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(index++, C_Currency_ID);
			pstmt.setTimestamp(index++, DateDoc);
			if (!RequestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_GLJournal) && !RequestType.equals(MFTUPaymentRequest.REQUESTTYPE_ARInvoice))
				pstmt.setTimestamp(index++, DateDoc);
			pstmt.setInt(index++, C_Currency_ID);
			pstmt.setTimestamp(index++, DateDoc);
			pstmt.setInt(index++, Env.getAD_Client_ID(Env.getCtx()));
			pstmt.setInt(index++, AD_Org_ID);
			
			if (X_FTU_PaymentRequest.REQUESTTYPE_APInvoice.equals(RequestType) || MFTUPaymentRequest.REQUESTTYPE_ARInvoice.equals(RequestType))
				pstmt.setTimestamp(index++, DateDoc);
			
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				Vector<Object> line = new Vector<Object>(9);
				line.add(Boolean.FALSE);           	//  0-Selection
				line.add(rs.getString(2));  		//  1-OrgName
				line.add(rs.getTimestamp(3));		//	2-DueDate
				line.add(rs.getString(4));  		//  3-BPName
				line.add(rs.getString(5));  		//  4-DocTypeName
				KeyNamePair pp = new KeyNamePair(rs.getInt(1), rs.getString(6).trim());
				line.add(pp);                       //  5-DocumentNo
				line.add(rs.getString(7));			// 	6-ISO_CODE
				line.add(rs.getBigDecimal(8));		// 	7-GrandTotal
				line.add(rs.getBigDecimal(9));		// 	8-DueAmt
				line.add(rs.getBigDecimal(10));		// 	9-PayAmt
				line.add(rs.getString(11).equals("Y"));		// 	10-IsTaxWithholding
				data.add(line);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}

		return data;
	}   //  loadShipment
	
	public ArrayList<KeyNamePair> getDocTypeData()
	{
		String RequestType = getGridTab().get_ValueAsString("RequestType");
		
		ArrayList<KeyNamePair> data = new ArrayList<KeyNamePair>();
		String sql = null;
		/**Document type**/
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			sql = MRole.getDefault().addAccessSQL(
				"SELECT d.C_DocType_ID, d.PrintName "
				+ "FROM C_DocType d "
				+ "WHERE d.DocBaseType = '"+RequestType+"' "
				+ "ORDER BY 2", "d",
				MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO);

			KeyNamePair dt = new KeyNamePair(0, "");
			data.add(dt);
			pstmt = DB.prepareStatement(sql, null);
			rs = pstmt.executeQuery();

			while (rs.next())
			{
				dt = new KeyNamePair(rs.getInt(1), rs.getString(2));
				data.add(dt);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		
		return data;
	}
}
