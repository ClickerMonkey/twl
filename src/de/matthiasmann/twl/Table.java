/*
 * Copyright (c) 2008-2009, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.matthiasmann.twl;

import de.matthiasmann.twl.model.TableModel;
import de.matthiasmann.twl.model.TreeTableNode;

/**
 *
 * @author Matthias Mann
 */
public class Table extends TableBase {

    private final TableModel.ChangeListener modelChangeListener;
    
    private TableModel model;

    public Table() {
        this.modelChangeListener = new ModelChangeListener();
    }

    public Table(TableModel model) {
        this();
        setModel(model);
    }

    public TableModel getModel() {
        return model;
    }

    public void setModel(TableModel model) {
        if(this.model != null) {
            this.model.removeChangeListener(modelChangeListener);
        }
        this.model = model;
        if(this.model != null) {
            numRows = model.getNumRows();
            numColumns = model.getNumColumns();
            this.model.addChangeListener(modelChangeListener);
        } else {
            numRows = 0;
            numColumns = 0;
        }
        modelAllChanged();
    }

    @Override
    protected Object getCellData(int row, int column, TreeTableNode node) {
        return model.getCell(row, column);
    }

    @Override
    protected TreeTableNode getNodeFromRow(int row) {
        return null;
    }

    class ModelChangeListener implements TableModel.ChangeListener {
        public void rowsInserted(int first, int last) {
            numRows = model.getNumRows();
            modelRowsInserted(first, last-first+1);
        }
        public void rowsDeleted(int first, int last) {
            numRows = model.getNumRows();
            modelRowsDeleted(first, last-first+1);
        }
        public void rowsChanged(int first, int last) {
            for(int row=first ; row<=last ; row++) {
                modelRowChanged(row);
            }
        }
        public void columnDeleted(int idx, int count) {
            numColumns = model.getNumColumns();
            throw new UnsupportedOperationException("Not supported yet.");
        }
        public void columnInserted(int idx, int count) {
            numColumns = model.getNumColumns();
            throw new UnsupportedOperationException("Not supported yet.");
        }
        public void cellChanged(int row, int column) {
            modelCellChanged(row, column);
        }
        public void allChanged() {
            numRows = model.getNumRows();
            numColumns = model.getNumColumns();
            modelAllChanged();
        }
    }
}
