/* 
 * 
 * Copyright (c) 2010, Regents of the University of California 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Neither the name of the Lawrence Berkeley National Lab nor the names of its contributors may be used to endorse 
 * or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 */
package org.paint.main;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bbop.framework.GUIManager;
import org.bbop.phylo.annotate.AnnotationUtil;
import org.bbop.phylo.gaf.GafPropagator;
import org.bbop.phylo.io.panther.PantherAdapterI;
import org.bbop.phylo.io.panther.PantherDbInfo;
import org.bbop.phylo.model.Bioentity;
import org.bbop.phylo.model.Family;
import org.bbop.phylo.tracking.LogUtil;
import org.bbop.phylo.tracking.Logger;
import org.bbop.phylo.util.Constant;
import org.bbop.phylo.util.TimerUtil;
import org.paint.config.PaintConfig;
import org.paint.displaymodel.DisplayTree;
import org.paint.gui.DirtyIndicator;
import org.paint.gui.event.EventManager;
import org.paint.gui.event.ProgressEvent;
import org.paint.gui.matrix.AnnotMatrix;
import org.paint.gui.matrix.AnnotMatrixModel;
import org.paint.gui.msa.MSA;
import org.paint.gui.msa.MSAPanel;
import org.paint.gui.table.GeneTable;
import org.paint.gui.table.GeneTableModel;
import org.paint.gui.tree.TreePanel;
import org.paint.panther.PaintPantherAdapter;

public class PaintManager {
	/**
	 * 
	 */
	private static PaintManager INSTANCE = null;

	//	private static final long serialVersionUID = 1L;

	private TreePanel tree_pane;
	private GeneTable genes_pane;
	private MSAPanel msa_pane;
	private AnnotMatrix annot_matrix;

	private static Family family;

	private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(PaintManager.class);

	private PaintManager() {
		// Exists only to defeat instantiation.
	}

	public static synchronized PaintManager inst() {
		if (INSTANCE == null) {
			INSTANCE = new PaintManager();
		}
		return INSTANCE;
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

	public Family getFamily() {
		return family;
	}

	public void setFamily(Family fam) {
		family = fam;
	}

	public void setGeneTable(GeneTable gp) {
		genes_pane = gp;
	}

	public void setTreePane(TreePanel tree) {
		this.tree_pane = tree;
	}

	public void setMSAPane(MSAPanel msa_pane) {
		this.msa_pane = msa_pane;
	}

	public GeneTable getGeneTable() {
		return genes_pane;
	}

	public int getTopMargin() {
		int margin = genes_pane.getTableHeader().getHeight();
		if (margin > 0)
			return margin;
		else
			return genes_pane.getRowHeight();
	}

	public int getRowHeight() {
		return genes_pane.getRowHeight();
	}

	public List<Bioentity> getRows() {
		if (tree_pane != null)
			return tree_pane.getTerminusNodes();
		else
			return null;
	}
	/**
	 * Method declaration
	 * 
	 * @param family_name
	 * @param useServer
	 * 
	 * @see
	 */
	public void openNewFamily(String family_name) {
		openFamily(family_name, false, true);
	}

	public void openActiveFamily(String family_name, boolean use_server) {
		openFamily(family_name, true, use_server);
	}

	private void openFamily(String family_name, boolean existing, boolean use_server) {

		TimerUtil timer = new TimerUtil();
		int progress_increment = (PaintConfig.inst().collapse_no_exp) ? 15 : 20;
		progress_increment += !existing? 6 : 0;
		int progress = 0;

		if (use_server) {
			fireProgressChange("Fetching " + family_name + " tree & MSA from PANTHERDB", progress, ProgressEvent.Status.START);
		} else {
			fireProgressChange("Loading " + family_name + " tree & MSA from local disk", progress, ProgressEvent.Status.START);
		}
		progress += progress_increment;

		family = new Family(family_name);
		DisplayTree tree = new DisplayTree(family_name);
		
		PantherAdapterI adapter = new PaintPantherAdapter(family_name, use_server);

		boolean success = family.fetch(tree, adapter);
		if (success) {
			setTitle();

			// Load the attr file to obtain the PTN #s
			GeneTableModel genes = new GeneTableModel(tree.getTerminusNodes());
			genes_pane.setModel(genes);

			// Need to do this after the attr table is set
			// because the tree node positions depends on the respective row positions
			tree_pane.setTreeModel(tree);

			if (family.getMsaContent() != null) {
				MSA msa = new MSA(family.getMsaContent(), family.getWtsContent());
				msa_pane.setModel(msa);
			}
			try {
				fireProgressChange("Fetching experimental annotations from GOLR", progress, ProgressEvent.Status.START);
				progress += progress_increment;

				log.info("Fetching experimental annotations from GOLR");
				success = AnnotationUtil.loadExperimental(family);
				if (success) {
					/*
					 * Don't bother with looking for these if they don't exist yet
					 */
					if (existing) {
						File family_dir = new File(PaintConfig.inst().gafdir);
						Logger.importUserLog(family.getFamily_name(), family_dir);

						fireProgressChange("Clearing disputed annotations", progress, ProgressEvent.Status.START);
						progress += progress_increment;
						GafPropagator.importChallenges(family, family_dir);

						fireProgressChange("Loading PAINT annotations from GAF file", progress, ProgressEvent.Status.START);
						progress += progress_increment;
						GafPropagator.importAnnotations(family, family_dir);

					} else {
						Logger.clearUserLog();
					}
				}
				else {
					family = null;
					fireProgressChange("Unable to retrieve experimental annotations " + family_name, 100, ProgressEvent.Status.END);
				}
				log.info("Retrieval of experimental annotations took " + timer.reportElapsedTime());

			} catch (Exception e) {
				success = false;
				log.error("Something wrong with clade NOTs: " + e.getMessage());
				e.printStackTrace();
			}

			if (success) {
				fireProgressChange("Initializing annotation matrix", progress, ProgressEvent.Status.START);
				progress += progress_increment;

				log.info("Initializing annotation matrix");
				annot_matrix.setModels(getTree().getTerminusNodes());

				DirtyIndicator.inst().dirtyGenes(false);

				if (PaintConfig.inst().collapse_no_exp) {
					fireProgressChange("Collapsing branches lacking experimental data", progress, ProgressEvent.Status.START);
					progress += progress_increment;

					tree_pane.collapseNonExperimental();
				}
				fireProgressChange("Notifying displays of new family", progress, ProgressEvent.Status.START);
				EventManager.inst().fireNewFamilyEvent(this, family);

				fireProgressChange(family_name + " is ready", 100, ProgressEvent.Status.END);
				
				log.info("Completed initialization of annotation matrix " + timer.reportElapsedTime());

			} else {
				family = null;
				fireProgressChange("Unable to open " + family_name, 100, ProgressEvent.Status.END);
			}
		} else {
			family = null;
			fireProgressChange("Unable to open " + family_name, 100, ProgressEvent.Status.END);			
		}
		log.info("Completed loading and initializing all data for " + family_name + " in "+ timer.reportElapsedTime());
	}

	public void saveFamily() {
		String username = System.getProperty("user.name");
		String program_name = PAINT.getAppID();
		File family_dir = new File(PaintConfig.inst().gafdir);
		String comment = "Saved by " + username + " using " + program_name + " on " + LogUtil.dateNow() + 
				" using " + Constant.PANTHER_VERSION + PantherDbInfo.getCurrentVersionName();
		family.save(family_dir, comment);
		DirtyIndicator.inst().dirtyGenes(false);
	}

	private static void fireProgressChange(String message, int percentageDone,
			ProgressEvent.Status status) {
		ProgressEvent event = new ProgressEvent(PaintManager.class, message,
				percentageDone, status);
		EventManager.inst().fireProgressEvent(event);
	}

	public List<String> findTerm(String cue) {
		Map<String, AnnotMatrixModel> annots = annot_matrix.getModels();
		List<String> term_list = new ArrayList<String>();
		if (annots != null) {
			for (String aspect : annots.keySet()) {
				AnnotMatrixModel annot_table = annots.get(aspect);
				List<String> partial_list = annot_table.searchForTerm(cue);
				term_list.addAll(partial_list);
			}
		}
		return term_list;
//		AnnotMatrixModel annot = annot_matrix.getAll();
//		List<String> term_list = new ArrayList<String>();
//		if (annot != null) {
//			term_list = annot.searchForTerm(cue);
//		}
//		return term_list;
	}

	/**
	 * Method declaration
	 * 
	 * 
	 * @param book
	 * 
	 * @see
	 */
	public void setTitle() {
		String title;
		Family family = getFamily();
		if (family.getFamily_name() != null) {
			title = family.getFamily_name();
			String description = family.getDescription();
			if (description != null && !description.contains("NOT NAMED")) {
				title += " (" + description + ")";
			}
			title +=  " - " + PAINT.getAppID();
		} else {
			title = PAINT.getAppID();
		}
		GUIManager.getManager().getFrame().setTitle(title);
	}

	public TreePanel getTree() {
		return tree_pane;
	}

	public MSAPanel getMSA() {
		return msa_pane;
	}

	public void setMatrix(AnnotMatrix annot_table) {
		annot_matrix = annot_table;
	}

	public AnnotMatrix getMatrix() {
		return annot_matrix;
	}

}
