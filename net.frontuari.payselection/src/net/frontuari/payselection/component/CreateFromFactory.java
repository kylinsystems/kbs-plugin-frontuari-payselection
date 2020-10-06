package net.frontuari.payselection.component;

import org.compiere.grid.ICreateFrom;
import org.compiere.grid.ICreateFromFactory;
import org.compiere.model.GridTab;

import net.frontuari.payselection.model.I_FTU_PaymentRequest;
import net.frontuari.payselection.webui.apps.form.WPRCreateFromDocs;

/**
 * Register Create From
 * @author Jorge Colmenarez, 2020-08-11 18:12, jcolmenarez@frontuari.net
 *
 */
public class CreateFromFactory implements ICreateFromFactory {
	@Override
	public ICreateFrom create(GridTab mTab) 
	{
		String tableName = mTab.getTableName();
		

		if (tableName.equals(I_FTU_PaymentRequest.Table_Name))
			return new WPRCreateFromDocs(mTab);
		
		return null;
	}
}
