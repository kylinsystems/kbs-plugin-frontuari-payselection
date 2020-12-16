/**
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Copyright (C) 2020 Frontuari, C.A. <http://frontuari.net> and contributors (see README.md file).
 */

package net.frontuari.payselection.component;

import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MPayment;
import org.compiere.model.X_C_DocType;
import org.compiere.model.X_C_PaySelectionLine;

import net.frontuari.payselection.base.FTUEventFactory;
import net.frontuari.payselection.model.FTUPaySelectionEvents;

/**
 * Event Factory
 */
public class EventFactory extends FTUEventFactory {

	/**
	 * For initialize class. Register the custom events to build
	 * 
	 * <pre>
	 * protected void initialize() {
	 * 	registerEvent(IEventTopics.DOC_BEFORE_COMPLETE, MTableExample.Table_Name, EPrintPluginInfo.class);
	 * }
	 * </pre>
	 */
	@Override
	protected void initialize() {
		registerEvent(IEventTopics.PO_AFTER_NEW, X_C_PaySelectionLine.Table_Name, FTUPaySelectionEvents.class);
		registerEvent(IEventTopics.PO_BEFORE_NEW, X_C_DocType.Table_Name, FTUPaySelectionEvents.class);
		registerEvent(IEventTopics.PO_BEFORE_CHANGE, X_C_DocType.Table_Name, FTUPaySelectionEvents.class);
		registerEvent(IEventTopics.DOC_AFTER_COMPLETE, MPayment.Table_Name, FTUPaySelectionEvents.class);
	}

}
