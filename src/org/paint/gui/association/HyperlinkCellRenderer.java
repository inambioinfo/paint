package org.paint.gui.association;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JTable;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.log4j.Logger;
import org.bbop.phylo.util.OWLutil;
import org.bbop.swing.ExtensibleLabelUI.Renderer;
import org.bbop.swing.HyperlinkLabel;
import org.paint.gui.AspectSelector;
import org.paint.gui.GuiConstant;
import org.paint.util.RenderUtil;

import owltools.gaf.GeneAnnotation;

public class HyperlinkCellRenderer extends DefaultTableCellRenderer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected static Logger log = Logger.getLogger(HyperlinkCellRenderer.class);

	protected HyperlinkLabel label;
	private Color bg_color;
	private boolean selected;
	
	public HyperlinkCellRenderer() {
		super();
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		selected = isSelected;
		setFont(GuiConstant.DEFAULT_FONT);
		setForeground(GuiConstant.FOREGROUND_COLOR);
		if (value != null) {
			label = (HyperlinkLabel) value;
			String tip = label.getToolTipText();
			setToolTipText(tip);
			UIManager.put("ToolTip.foreground", GuiConstant.FOREGROUND_COLOR);
			ToolTipManager.sharedInstance().setDismissDelay(999999999);
		}
		GeneAnnotation assoc = ((AssociationsTableModel) table.getModel()).getEvidenceForRow(row);
		String aspect_name = AspectSelector.inst().getAspectName4Code(OWLutil.inst().getAspect(assoc.getCls()));
		bg_color = RenderUtil.getAspectColor(aspect_name);
		return this;
	}
	
	@Override
	public void paint(Graphics g) {
		Rectangle bounds = getBounds();
		Rectangle local_bounds = new Rectangle(0, 0, bounds.width, bounds.height);
		RenderUtil.paintBorder(g, local_bounds, bg_color, selected);
		if (label != null) {
			Renderer r = (Renderer) label.getClientProperty(BasicHTML.propertyKey);
			r.paint(g, local_bounds);
		}
	}

}
