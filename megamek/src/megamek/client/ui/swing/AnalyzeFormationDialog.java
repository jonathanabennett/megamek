/**
 * 
 */
package megamek.client.ui.swing;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

import megamek.client.ratgenerator.FormationType;
import megamek.client.ratgenerator.ModelRecord;
import megamek.client.ratgenerator.RATGenerator;
import megamek.client.ratgenerator.UnitTable;
import megamek.client.ui.Messages;
import megamek.common.EntityWeightClass;
import megamek.common.MechSummary;

/**
 * Shows a table of all units matching the chosen faction/unit type/era parameters and
 * general criteria for a formation along with data relevant to the formation constraints.
 * User can select combinations of additional criteria to see which units meet those criteria
 * as well.  
 * 
 * @author Neoancient
 *
 */
public class AnalyzeFormationDialog extends JDialog {

    private static final long serialVersionUID = 6487681030307585648L;

    private JTable tblUnits;
    private TableRowSorter<UnitTableModel> tableSorter;
    
    private FormationType formationType;
    private List<MechSummary> units = new ArrayList<>();
    private List<JCheckBox> otherCriteriaChecks = new ArrayList<>();
    private List<FormationType.Constraint> allConstraints = new ArrayList<>();
    
    public AnalyzeFormationDialog(JFrame frame, FormationType ft, List<UnitTable.Parameters> params,
            int numUnits, int networkMask) {
        super(frame, Messages.getString("AnalyzeFormationDialog.title"), true);
        formationType = ft;
        ft.getOtherCriteria().forEachRemaining(c -> allConstraints.add(c));
        allConstraints.addAll(networkConstraints(networkMask));
        
        getContentPane().setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        //Add to a set to avoid duplicates, but dump into a list so the table can have an ordered collection
        Set<MechSummary> unitSet = new HashSet<>();
        params.forEach(p -> {
            UnitTable table = UnitTable.findTable(p);
            for (int i = 0; i < table.getNumEntries(); i++) {
                MechSummary ms = table.getMechSummary(i);
                if (ms != null && ft.getMainCriteria().test(ms)) {
                    unitSet.add(ms);
                }
            }
        });
        units.addAll(unitSet);
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 5, 0, 5);
        gbc.gridwidth = 3;
        gbc.weightx = 0;
        gbc.weighty = 0;
        mainPanel.add(new JLabel(Messages.getString("AnalyzeFormationDialog.formation") + ft.getName()),
                gbc);
        
        gbc.gridy++;
        StringBuilder sb = new StringBuilder(Messages.getString("AnalyzeFormationDialog.weightClassRange"));
        sb.append(": ").append(EntityWeightClass.getClassName(ft.getMinWeightClass()));
        if (ft.getMinWeightClass() != ft.getMaxWeightClass()) {
            sb.append("-").append(EntityWeightClass.getClassName(ft.getMaxWeightClass()));
        }
        mainPanel.add(new JLabel(sb.toString()), gbc);
        
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(new JLabel(Messages.getString("AnalyzeFormationDialog.required")), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(new JLabel(Messages.getString("AnalyzeFormationDialog.constraint")), gbc);
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(new JLabel(Messages.getString("AnalyzeFormationDialog.available")), gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(new JLabel(Messages.getString("AnalyzeFormationDialog.all")), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        if (ft.getMainDescription() != null) {
            mainPanel.add(new JLabel(ft.getMainDescription()), gbc);
        } else {
            mainPanel.add(new JLabel("-"), gbc);
        }
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(new JLabel(String.valueOf(units.size())), gbc);
        
        allConstraints.forEach(c -> {
            JCheckBox chk = new JCheckBox(c.getDescription());
            otherCriteriaChecks.add(chk);
            chk.addChangeListener(ev -> filter());
            gbc.gridy++;
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            mainPanel.add(new JLabel(String.valueOf(c.getMinimum(numUnits))), gbc);
            gbc.gridx = 1;
            gbc.anchor = GridBagConstraints.WEST;
            mainPanel.add(chk, gbc);
            gbc.gridx = 2;
            gbc.anchor = GridBagConstraints.CENTER;
            mainPanel.add(new JLabel(String.valueOf(units.stream()
                    .filter(ms -> c.matches(ms)).count())), gbc);
        });
        
        if (ft.getGroupingCriteria() != null
                && ft.getGroupingCriteria().appliesTo(params.get(0).getUnitType())) {
            gbc.gridy++;
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            mainPanel.add(new JLabel(String.valueOf(ft.getGroupingCriteria().getMinimum(numUnits))), gbc);
            gbc.gridx = 1;
            gbc.anchor = GridBagConstraints.WEST;
            if (ft.getGroupingCriteria().hasGeneralCriteria()) {
                JCheckBox chk = new JCheckBox(String.format(Messages.getString("AnalyzeFormationDialog.groups.format"),
                        ft.getGroupingCriteria().getDescription(),
                        Math.min(numUnits, ft.getGroupingCriteria().getGroupSize())));
                otherCriteriaChecks.add(chk);
                chk.addChangeListener(ev -> filter());
                mainPanel.add(chk, gbc);
            } else {
                mainPanel.add(new JLabel(String.format(Messages.getString("AnalyzeFormationDialog.groups.format"),
                        ft.getGroupingCriteria().getDescription(),
                        Math.min(numUnits, ft.getGroupingCriteria().getGroupSize()))),
                        gbc);
            }
            gbc.gridx = 2;
            gbc.anchor = GridBagConstraints.CENTER;
            mainPanel.add(new JLabel(String.valueOf(units.stream()
                    .filter(ms -> ft.getGroupingCriteria().matches(ms)).count())), gbc);
        }
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weighty = 1.0;
        mainPanel.add(new JLabel(""), gbc);
        
        UnitTableModel model = new UnitTableModel();
        tblUnits = new JTable(model);
        tableSorter = new TableRowSorter<>(model);
        tableSorter.setComparator(UnitTableModel.COL_MOVEMENT,
                (m1, m2) ->  Integer.valueOf(m1.toString().replaceAll("\\D.*", "")).compareTo(
                        Integer.valueOf(m2.toString().replaceAll("\\D.*", ""))));
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(UnitTableModel.COL_NAME, SortOrder.ASCENDING));
        tableSorter.setSortKeys(sortKeys);
        tblUnits.setRowSorter(tableSorter);
        
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.gridheight = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        mainPanel.add(new JScrollPane(tblUnits), gbc);
        
        getContentPane().add(mainPanel, BorderLayout.CENTER);
        
        JButton btnOk = new JButton(Messages.getString("Okay"));
        btnOk.addActionListener(ev -> setVisible(false));
        getContentPane().add(btnOk, BorderLayout.SOUTH);
        
        pack();
    }
    
    private void filter() {
        List<RowFilter<UnitTableModel,Integer>> filters = new ArrayList<>();
        for (int i = 0; i < allConstraints.size(); i++) {
            if (otherCriteriaChecks.get(i).isSelected()) {
                filters.add(new UnitTableRowFilter(allConstraints.get(i)));
            }            
        }
        if (otherCriteriaChecks.size() > allConstraints.size()
                && otherCriteriaChecks.get(otherCriteriaChecks.size() - 1).isSelected()) {
            filters.add(new UnitTableRowFilter(formationType.getGroupingCriteria()));
        }
        tableSorter.setRowFilter(RowFilter.andFilter(filters));
    }
    
    private List<FormationType.Constraint> networkConstraints(int networkMask) {
        List<FormationType.Constraint> retVal = new ArrayList<>();
        switch(networkMask) {
        case ModelRecord.NETWORK_C3_MASTER:
            retVal.add(new FormationType.CountConstraint(1,
                    ms -> (getNetworkMask(ms) & ModelRecord.NETWORK_C3_MASTER) != 0, "C3 Master"));
            retVal.add(new FormationType.CountConstraint(3,
                    ms -> (getNetworkMask(ms) & ModelRecord.NETWORK_C3_SLAVE) != 0, "C3 Slave"));
            break;
        case ModelRecord.NETWORK_COMPANY_COMMAND:
            retVal.add(new FormationType.CountConstraint(1,
                    ms -> (getNetworkMask(ms) & ModelRecord.NETWORK_COMPANY_COMMAND) != 0, "C3 Master x 2"));
            retVal.add(new FormationType.CountConstraint(3,
                    ms -> (getNetworkMask(ms) & ModelRecord.NETWORK_C3_SLAVE) != 0, "C3 Slave"));
            break;
        case ModelRecord.NETWORK_C3I:
            retVal.add(new FormationType.CountConstraint(1,
                    ms -> (getNetworkMask(ms) & ModelRecord.NETWORK_C3I) != 0, "C3i"));
            break;
        case ModelRecord.NETWORK_BOOSTED_MASTER:
            retVal.add(new FormationType.CountConstraint(1,
                    ms -> (getNetworkMask(ms) & ModelRecord.NETWORK_BOOSTED_MASTER) != 0, "C3 Boosted Master"));
            retVal.add(new FormationType.CountConstraint(3,
                    ms -> (getNetworkMask(ms) & ModelRecord.NETWORK_BOOSTED_SLAVE) != 0, "C3 Boosted Slave"));
            break;
        case ModelRecord.NETWORK_NOVA:
            retVal.add(new FormationType.CountConstraint(3,
                    ms -> (getNetworkMask(ms) & ModelRecord.NETWORK_NOVA) != 0, "Nova CEWS"));
            break;
        }
        return retVal;
    }
    
    private int getNetworkMask(MechSummary ms) {
        ModelRecord mRec = RATGenerator.getInstance().getModelRecord(ms.getName());
        return mRec == null? ModelRecord.NETWORK_NONE : mRec.getNetworkMask();
    }
    
    class UnitTableRowFilter extends RowFilter<UnitTableModel,Integer> {
        FormationType.Constraint constraint;

        public UnitTableRowFilter(FormationType.Constraint constraint) {
            this.constraint = constraint;
        }
        
        @Override
        public boolean include(Entry<? extends UnitTableModel,? extends Integer> entry) {
            return constraint.matches(units.get(entry.getIdentifier()));
        }                    
    }
    
    class UnitTableModel extends AbstractTableModel {
        
        private static final long serialVersionUID = -1543320699765809458L;

        private static final int COL_NAME = 0;
        private static final int COL_WEIGHT_CLASS = 1;
        private static final int COL_MOVEMENT = 2;
        private static final int COL_ROLE = 3;
        private List<String> colNames = new ArrayList<>();
        
        public UnitTableModel() {
            colNames.add("Name");
            colNames.add("Weight Class");
            colNames.add("Movement");
            colNames.add("Role");
            formationType.getReportMetricKeys().forEachRemaining(k -> colNames.add(k));
        }

        @Override
        public int getRowCount() {
            return units.size();
        }

        @Override
        public int getColumnCount() {
            return colNames.size();
        }
        
        @Override
        public String getColumnName(int columnIndex) {
            return colNames.get(columnIndex);
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (units.isEmpty()) {
                return Object.class;
            }
            return getValueAt(0, columnIndex).getClass();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MechSummary ms = units.get(rowIndex);
            switch(columnIndex) {
            case COL_NAME:
                return ms.getName();
            case COL_WEIGHT_CLASS:
                return EntityWeightClass.getClassName(EntityWeightClass.getWeightClass(ms.getTons(), ms.getUnitType()));
            case COL_MOVEMENT:
                StringBuilder sb = new StringBuilder();
                sb.append(String.valueOf(ms.getWalkMp())).append("/")
                        .append(String.valueOf(ms.getRunMp()));
                if (formationType.isGround()) {
                    sb.append("/").append(String.valueOf(ms.getJumpMp()));
                }
                return sb.toString();
            case COL_ROLE:
                ModelRecord mr = RATGenerator.getInstance().getModelRecord(ms.getName());
                FormationType.UnitRole r = mr == null? FormationType.UnitRole.UNDETERMINED : mr.getUnitRole();
                return r.toString();
            default:
                Function<MechSummary,?> metric = formationType.getReportMetric(colNames.get(columnIndex));
                return metric == null? "?" : metric.apply(ms);
            }
        }
    }
}
