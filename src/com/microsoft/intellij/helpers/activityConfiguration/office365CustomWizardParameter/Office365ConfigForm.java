/**
 * Copyright (c) Microsoft Corporation
 * <p/>
 * All rights reserved.
 * <p/>
 * MIT License
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.microsoft.intellij.helpers.activityConfiguration.office365CustomWizardParameter;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.microsoft.directoryservices.Application;
import com.microsoft.intellij.forms.CreateOffice365ApplicationForm;
import com.microsoft.intellij.forms.PermissionsEditorForm;
import com.microsoft.intellij.helpers.ReadOnlyCellTableModel;
import com.microsoft.intellij.helpers.graph.ServicePermissionEntry;
import com.microsoft.intellij.helpers.o365.Office365Manager;
import com.microsoft.intellij.helpers.o365.Office365ManagerImpl;
import com.microsoft.tooling.msservices.components.DefaultLoader;
import com.microsoft.tooling.msservices.helpers.StringHelper;
import com.microsoft.tooling.msservices.model.Office365Permission;
import com.microsoft.tooling.msservices.model.Office365PermissionList;
import com.microsoft.tooling.msservices.model.Office365Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Vector;

public class Office365ConfigForm extends DialogWrapper {

    private JPanel rootPanel;
    private JComboBox cmbApps;
    private JTable tblAppPermissions;
    private JButton btnAddApp;
    private JButton btnSignOut;
    private JEditorPane editorSummary;

    public Office365ConfigForm(final Project project, boolean isListServices, boolean isFileServices, boolean isOutlookServices) {
        super(project, true);

        setTitle("Configure Office365 Services");

        this.tblAppPermissions.setFocusable(false);
        this.tblAppPermissions.setRowHeight(35);
        this.tblAppPermissions.setIntercellSpacing(new Dimension(5, 2));
        this.tblAppPermissions.setDefaultRenderer(Office365PermissionList.class, new AppPermissionsCR(tblAppPermissions));
        this.tblAppPermissions.setDefaultEditor(Office365PermissionList.class, new AppPermissionsCR(tblAppPermissions));

        this.cmbApps.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Application app = (Application) cmbApps.getSelectedItem();
                refreshPermissions(app);
            }
        });

        btnAddApp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                final CreateOffice365ApplicationForm form = new CreateOffice365ApplicationForm(project);
                form.setOnRegister(new Runnable() {
                    @Override
                    public void run() {
                        refreshApps(form.getApplication().getappId());
                    }
                });

                form.show();

            }
        });

        btnSignOut.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // clear the authentication token
                Office365ManagerImpl.getManager().clearAuthentication();

                // refresh apps to cause the sign in popup to be displayed
                refreshApps(null);
            }
        });

        refreshApps(null);

        updateSummary(project, isOutlookServices, isFileServices, isListServices);

        init();
    }

    public Application getApplication() {
        return (Application) cmbApps.getSelectedItem();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return rootPanel;
    }

    private void updateSummary(Project project, boolean isOutlookServices, boolean isFileServices, boolean isListServices) {
        StringBuilder summary = new StringBuilder();
        summary.append("<html> <head> </head> <body style=\"font-family: sans serif;\"> <p style=\"margin-top: 0\">" +
                "<b>Summary:</b></p> <ol> ");

        if (isOutlookServices) {
            summary.append("<li>Will add a reference to the Outlook Services library in project <b>");
            summary.append(project.getName());
            summary.append("</b>.</li> ");
            summary.append("<li>Will add a static method to instantiate OutlookClient and list messages.</li> ");
        }


        if (isFileServices) {
            summary.append("<li>Will add a reference to the File Services library in project <b>");
            summary.append(project.getName());
            summary.append("</b>.</li> ");
            summary.append("<li>Will add a static method to instantiate SharePointClient and list files.</li> ");
        }

        if (isListServices) {
            summary.append("<li>Will add a reference to the SharePoint Lists library in project <b>");
            summary.append(project.getName());
            summary.append("</b>.</li> ");
            summary.append("<li>Will add a static method to instantiate SharepointListsClient and enumerate lists.</li> ");
        }

        summary.append("<li>Will configure the Office 365 Activity referencing the mentioned static methods.</li> ");
        summary.append("<li>You can follow the link to <a href=\"https://github.com/OfficeDev/Office-365-SDK-for-Android\">" +
                "Office 365 SDK for Android</a> to learn more about the referenced libraries.</li> ");


        summary.append("</ol> <p style=\"margin-top: 0\">After clicking Finish, it might take a few seconds to " +
                "complete set up.</p> </body> </html>");

        editorSummary.setText(summary.toString());
    }

    private void fillApps(final String selectedAppId) {
        final Office365ConfigForm office365ConfigForm = this;

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                cmbApps.setRenderer(new StringComboBoxItemRenderer());
                cmbApps.setModel(new DefaultComboBoxModel(new String[]{"(loading...)"}));
                cmbApps.setEnabled(false);

                ReadOnlyCellTableModel messageTableModel = new ReadOnlyCellTableModel();
                messageTableModel.addColumn("Message");
                Vector<String> vector = new Vector<String>();
                vector.add("(loading... )");
                messageTableModel.addRow(vector);
                tblAppPermissions.setModel(messageTableModel);
            }
        }, ModalityState.any());

        final Office365Manager manager = Office365ManagerImpl.getManager();

        try {
            if (!manager.authenticated()) {
                manager.authenticate();

                // if we still don't have an authentication token then the
                // user has cancelled out of login; so we cancel out of this
                // wizard
                if (!manager.authenticated()) {
                    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            office365ConfigForm.close(DialogWrapper.CANCEL_EXIT_CODE);
                        }
                    }, ModalityState.any());
                    return;
                }
            }

            Futures.addCallback(manager.getApplicationList(), new FutureCallback<List<Application>>() {
                @Override
                public void onSuccess(final List<Application> applications) {
                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (applications.size() > 0) {
                                cmbApps.setRenderer(new ListCellRendererWrapper<Application>() {
                                    @Override
                                    public void customize(JList jList, Application application, int i, boolean b, boolean b2) {
                                        setText(application.getdisplayName());
                                    }
                                });

                                cmbApps.setModel(new DefaultComboBoxModel(applications.toArray()));
                                cmbApps.setEnabled(true);

                                int selectedIndex = 0;
                                if (!StringHelper.isNullOrWhiteSpace(selectedAppId)) {
                                    selectedIndex = Iterables.indexOf(applications, new Predicate<Application>() {
                                        @Override
                                        public boolean apply(Application application) {
                                            return application.getappId().equals(selectedAppId);
                                        }
                                    });
                                }
                                cmbApps.setSelectedIndex(Math.max(0, selectedIndex));
                            } else {
                                cmbApps.setRenderer(new StringComboBoxItemRenderer());
                                cmbApps.setModel(new DefaultComboBoxModel(new String[]{"No apps configured)"}));
                                cmbApps.setEnabled(false);

                                ReadOnlyCellTableModel messageTableModel = new ReadOnlyCellTableModel();
                                messageTableModel.addColumn("Message");
                                Vector<String> vector = new Vector<String>();
                                vector.add("There are no applications configured.");
                                messageTableModel.addRow(vector);
                                tblAppPermissions.setModel(messageTableModel);
                                //tblAppPermissions.setEnabled(false);
                            }
                        }
                    }, ModalityState.any());
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            DefaultLoader.getUIHelper().showException("An error occurred while attempting to fetch the " +
                                            "list of applications.", throwable,
                                    "Microsoft Cloud Services For Android - Error Fetching Applications", false, true);
                        }
                    }, ModalityState.any());
                }
            });
        } catch (Throwable throwable) {
            DefaultLoader.getUIHelper().showException("An error occurred while attempting to authenticate with Office 365.", throwable,
                    "Microsoft Cloud Services For Android - Error Authenticating O365", false, true);
        }
    }

    private void fillPermissions(@NotNull Application app) {
        // show a status message while we're fetching permissions
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                ReadOnlyCellTableModel messageTableModel = new ReadOnlyCellTableModel();
                messageTableModel.addColumn("Message");
                Vector<String> vector = new Vector<String>();
                vector.add("(loading... )");
                messageTableModel.addRow(vector);
                tblAppPermissions.setModel(messageTableModel);

                //tblAppPermissions.setEnabled(false);
            }
        }, ModalityState.any());

        Futures.addCallback(Office365ManagerImpl.getManager().getO365PermissionsForApp(app.getobjectId()), new FutureCallback<List<ServicePermissionEntry>>() {
            @Override
            public void onSuccess(final List<ServicePermissionEntry> servicePermissionEntries) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (servicePermissionEntries.size() > 0) {
                            tblAppPermissions.setModel(new AppPermissionsTM(servicePermissionEntries));
                            tblAppPermissions.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                            final TableColumn servicesColumn = tblAppPermissions.getColumnModel().getColumn(0);
                            servicesColumn.setMinWidth(100);
                            servicesColumn.setMaxWidth(250);
                            servicesColumn.setPreferredWidth(185);
                        } else {
                            ReadOnlyCellTableModel messageTableModel = new ReadOnlyCellTableModel();
                            messageTableModel.addColumn("Message");
                            Vector<String> vector = new Vector<String>();
                            vector.add("There are no Office 365 application permissions.");
                            messageTableModel.addRow(vector);
                            tblAppPermissions.setModel(messageTableModel);
                        }
                    }
                }, ModalityState.any());
            }

            @Override
            public void onFailure(Throwable throwable) {
                DefaultLoader.getUIHelper().showException("An error occurred while attempting to fetch permissions for " +
                                "Office 365 services.", throwable,
                        "Microsoft Cloud Services For Android - Error Fetching Permissions", false, true);
            }
        });
    }

    private void refreshApps(final String selectedAppId) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                fillApps(selectedAppId);
            }
        });
    }

    private void refreshPermissions(@NotNull final Application app) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                fillPermissions(app);
            }
        });
    }

    private void createUIComponents() {
        tblAppPermissions = new JBTable();
    }

    private class StringComboBoxItemRenderer extends ListCellRendererWrapper<String> {
        @Override
        public void customize(JList jList, String s, int i, boolean b, boolean b2) {
            setText(s);
        }
    }


    private class AppPermissionsTM extends AbstractTableModel {
        List<ServicePermissionEntry> servicePermissionEntries;

        public AppPermissionsTM(@NotNull List<ServicePermissionEntry> servicePermissionEntries) {
            this.servicePermissionEntries = servicePermissionEntries;
        }

        public List<ServicePermissionEntry> getPermissionEntries() {
            return servicePermissionEntries;
        }

        @Override
        public int getRowCount() {
            return servicePermissionEntries.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                return servicePermissionEntries.get(rowIndex).getKey();
            } else if (columnIndex == 1) {
                return servicePermissionEntries.get(rowIndex).getValue();
            } else {
                throw new IndexOutOfBoundsException("columnIndex");
            }
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 1) {
                servicePermissionEntries.get(rowIndex).setValue((Office365PermissionList) value);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        @Override
        public Class getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Office365Service.class;
            } else if (columnIndex == 1) {
                return Office365PermissionList.class;
            } else {
                throw new IndexOutOfBoundsException("columnIndex");
            }
        }

        @Override
        public String getColumnName(int columnIndex) {
            if (columnIndex == 0) {
                return "Service";
            } else if (columnIndex == 1) {
                return "Permissions";
            } else {
                throw new IndexOutOfBoundsException("columnIndex");
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                return false;
            } else if (columnIndex == 1) {
                return true;
            }

            throw new IndexOutOfBoundsException("columnIndex");
        }
    }

    private class AppPermissionsCR extends AbstractCellEditor implements TableCellEditor, TableCellRenderer {
        private JPanel panel;
        private Office365Service service;
        private Office365PermissionList permissionSet;
        private JLabel permissionsLabel;
        private JTable tblAppPermissions;
        private int currentRow, currentCol;

        public AppPermissionsCR(JTable tblAppPermissions) {
            this.tblAppPermissions = tblAppPermissions;
            FormLayout formLayout = new FormLayout(
                    "fill:70px:grow, fill:30px",
                    "center:d:noGrow"
            );
            panel = new JPanel(formLayout);
            panel.setFocusable(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            return getTableCellComponent(table, (Office365PermissionList) value, row);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            currentRow = row;
            currentCol = column;
            return getTableCellComponent(table, (Office365PermissionList) value, row);
        }

        @Override
        public Object getCellEditorValue() {
            return permissionSet;
        }

        private Component getTableCellComponent(JTable table, Office365PermissionList permissionSet, int row) {
            this.permissionSet = permissionSet;
            this.service = (Office365Service) table.getModel().getValueAt(row, 0);

            // build the label text
            Iterable<Office365Permission> enabledPermissions = Iterables.filter(this.permissionSet, new Predicate<Office365Permission>() {
                @Override
                public boolean apply(Office365Permission office365Permission) {
                    return office365Permission.isEnabled();
                }
            });

            String permissions = Joiner.on(", ").join(Iterables.transform(enabledPermissions, new Function<Office365Permission, String>() {
                @Override
                public String apply(Office365Permission office365Permission) {
                    return office365Permission.getName();
                }
            }));

            if (StringHelper.isNullOrWhiteSpace(permissions)) {
                permissions = "No permissions assigned";
            }

            // setting this to true causes the panel to not draw a background;
            // if we don't do this then the panel draws the default dialog
            // background color which looks ugly in a light colored theme
            panel.setOpaque(false);

            // create the label and the button
            if (permissionsLabel == null) {
                panel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

                CellConstraints constraints = new CellConstraints();
                permissionsLabel = new JBLabel();
                panel.add(permissionsLabel, constraints.xy(1, 1));

                JButton button = new JButton("...");
                button.setOpaque(true);
                panel.add(button, constraints.xy(2, 1));

                button.addActionListener(new ShowPermissionsDialogActionListener());
            }

            permissionsLabel.setText(permissions);
            permissionsLabel.setToolTipText(permissions);
            return panel;
        }

        class ShowPermissionsDialogActionListener implements ActionListener {
            @Override
            public void actionPerformed(ActionEvent e) {
                // this is not exactly intuitive but when you click the button on the table cell
                // this is the method that gets called; so we pop up the permissions form here
                final PermissionsEditorForm permissionsEditorForm = new PermissionsEditorForm(service.getName(), permissionSet, null);

                permissionsEditorForm.setOnOK(new Runnable() {
                    @Override
                    public void run() {
                        // update our permissions
                        permissionSet = permissionsEditorForm.getPermissions();
                        tblAppPermissions.getModel().setValueAt(permissionSet, currentRow, currentCol);
                        fireEditingStopped();
                    }
                });

                permissionsEditorForm.show();

            }
        }
    }
}
