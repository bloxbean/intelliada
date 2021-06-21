package com.bloxbean.intelliada.idea.metadata.ui.editor;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.metadata.helper.MetadataToJsonNoSchemaConverter;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.intelliada.idea.common.ui.JsonEditorTextField;
import com.bloxbean.intelliada.idea.metadata.exception.InvalidMetadataException;
import com.bloxbean.intelliada.idea.metadata.util.editor.CBORMetadataEx;
import com.bloxbean.intelliada.idea.metadata.util.editor.CBORMetadataListEx;
import com.bloxbean.intelliada.idea.metadata.util.editor.CBORMetadataMapEx;
import com.bloxbean.intelliada.idea.metadata.util.editor.MetadataModelToTreeModelBuilder;
import com.bloxbean.intelliada.idea.util.MessageUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.math.BigInteger;

public class TreeMetadataEditor {
    private JPanel mainPanel;
    private Tree tree;
    private EditorTextField editorTf;
    private Project project;
    private ObjectMapper mapper;
    private DefaultTreeModel treeModel;

    public TreeMetadataEditor(Project project) {
        this.project = project;
        this.mapper = new ObjectMapper();
        tree.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(Component comp, int x, int y) {
                popupInvoked(comp, x, y);
            }
        });
    }

    private void popupInvoked(Component comp, int x, int y) {
        final TreePath path = tree.getSelectionPath();

        final DefaultActionGroup group = new DefaultActionGroup();
        final DefaultMutableTreeNode node;
        Object userObject = null;
        if (path != null) {
            node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node != null) {
                userObject = node.getUserObject();
            } else {
                return;
            }
        } else {
            return;
        }

        if (userObject instanceof CBORMetadata) {
            group.add(new AddLabelAction(node));
            group.add(new BuildTreeAction(node));
        } else if (userObject instanceof CBORMetadataMap) {
            group.add(new AddKeyValueAction(node));
        } else if (userObject instanceof CBORMetadataList) {
            group.add(new AddValueAction(node));
        } else if(node instanceof KeyValueLeafNode || node instanceof SingleValueLeafNode) {
            group.add(new EditAction(node));
        }

        if (!(userObject instanceof CBORMetadata)) {
            group.add(new RemoveLabelAction(node));
        }
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("menu_popup_metadata", group);
        popupMenu.getComponent().show(comp, x, y);
    }

    public void updateEditorText() {
        Object root = tree.getModel().getRoot();
        if (root instanceof RootNode) {
            CBORMetadata metadata = ((RootNode) root).getCBORMetadata();
            String jsonStr = MetadataToJsonNoSchemaConverter.cborBytesToJson(metadata.serialize());
            try {
                Object json = mapper.readValue(jsonStr, Object.class);
                jsonStr = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            } catch (JsonProcessingException jsonProcessingException) {
                //jsonProcessingException.printStackTrace();
            }
            editorTf.setText(jsonStr);
        }
    }

    public Metadata getMetadata() {
        RootNode rootNode = (RootNode)(treeModel.getRoot());
        return rootNode.getCBORMetadata();
    }

    abstract class BaseAction extends AnAction {
        Object userObject;
        DefaultMutableTreeNode node;

        public BaseAction(DefaultMutableTreeNode node, String label, String label1, Icon icon) {
            super(label, label1, icon);
            this.node = node;
            if (this.node != null)
                this.userObject = node.getUserObject();
        }

        protected void setTopLevelMapValue(BigInteger key, Object value) {
            if (userObject instanceof CBORMetadataEx) {
                ((CBORMetadataEx) userObject).putValue(key, value);
            }
        }

        protected void setValue(String key, Object value) {
            if (userObject instanceof CBORMetadataMapEx) {
                ((CBORMetadataMapEx) userObject).putValue(key, value);
            } else if (userObject instanceof CBORMetadataListEx) {
                ((CBORMetadataListEx) userObject).addValue(value);
            }
        }

    }

    class AddLabelAction extends BaseAction {
        public AddLabelAction(DefaultMutableTreeNode node) {
            super(node, "Add Label", "Add Label", AllIcons.General.Add);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            KeyValueInputDialog dialog = new KeyValueInputDialog(project, true);
            boolean ok = dialog.showAndGet();
            if (!ok) return;
            DataType type = dialog.getKeyValueForm().getType();
            BigInteger key = dialog.getKeyValueForm().getKeyAsBI();
            Object value = null;
            if (type == DataType.String || type == DataType.Int || type == DataType.Bytes) {
                value = dialog.getKeyValueForm().getValue();
                node.add(new KeyValueLeafNode(type, key, value));
                setTopLevelMapValue(key, value);
            } else if (type == DataType.Map) {
                value = CBORMetadataMapEx.create();
                node.add(new MapNode(key, (CBORMetadataMapEx) value));
                setTopLevelMapValue(key, value);
            } else if (type == DataType.List) {
                value = CBORMetadataListEx.create();
                node.add(new ListNode(key, (CBORMetadataListEx) value));
                setTopLevelMapValue(key, value);
            }

            if (userObject == null)
                return;

            if (node != null) {
                ((DefaultTreeModel) tree.getModel()).nodesWereInserted(
                        node, new int[]{node.getChildCount() - 1});
            }

            updateEditorText();
        }

        @Override
        public boolean isDumbAware() {
            return true;
        }
    }

    class AddKeyValueAction extends BaseAction {
        public AddKeyValueAction(DefaultMutableTreeNode node) {
            super(node, "Add Key/Value", "Add Key/Value", AllIcons.General.Add);
            this.node = node;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            KeyValueInputDialog dialog = new KeyValueInputDialog(project);
            boolean ok = dialog.showAndGet();
            if (!ok) return;
            DataType type = dialog.getKeyValueForm().getType();
            String key = dialog.getKeyValueForm().getKey();
            Object value = null;
            if (type == DataType.String || type == DataType.Int || type == DataType.Bytes) {
                value = dialog.getKeyValueForm().getValue();
                node.add(new KeyValueLeafNode(type, String.valueOf(key), value));
                setValue(key, value);
            } else if (type == DataType.Map) {
                value = CBORMetadataMapEx.create();
                node.add(new MapNode(String.valueOf(key), (CBORMetadataMapEx) value));
                setValue(key, value);
            } else if (type == DataType.List) {
                value = CBORMetadataListEx.create();
                node.add(new ListNode(String.valueOf(key), (CBORMetadataListEx) value));
                setValue(key, value);
            }

            if (userObject == null)
                return;

            if (node != null) {
                ((DefaultTreeModel) tree.getModel()).nodesWereInserted(
                        node, new int[]{node.getChildCount() - 1});
            }

            updateEditorText();
        }

        @Override
        public boolean isDumbAware() {
            return true;
        }
    }

    class AddValueAction extends BaseAction {
        public AddValueAction(DefaultMutableTreeNode node) {
            super(node, "Add Item", "Add Item", AllIcons.General.Add);
            this.node = node;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            KeyValueInputDialog dialog = new KeyValueInputDialog(project);
            dialog.enableListMode();
            boolean ok = dialog.showAndGet();
            if (!ok) return;
            DataType type = dialog.getKeyValueForm().getType();
            Object value = null;
            if (type == DataType.String || type == DataType.Int || type == DataType.Bytes) {
                value = dialog.getKeyValueForm().getValue();
                node.add(new SingleValueLeafNode(type, value));
                setValue(null, value);
            } else if (type == DataType.Map) {
                value = CBORMetadataMapEx.create();
                node.add(new MapNode(null, (CBORMetadataMapEx) value));
                setValue(null, value);
            } else if (type == DataType.List) {
                value = CBORMetadataListEx.create();
                node.add(new ListNode(null, (CBORMetadataListEx) value));
                setValue(null, value);
            }

            if (userObject == null)
                return;

            if (node != null) {
                ((DefaultTreeModel) tree.getModel()).nodesWereInserted(
                        node, new int[]{node.getChildCount() - 1});
            }

            updateEditorText();
        }

        @Override
        public boolean isDumbAware() {
            return true;
        }
    }

    class RemoveLabelAction extends BaseAction {
        public RemoveLabelAction(DefaultMutableTreeNode node) {
            super(node, "Remove", "Remove", AllIcons.General.Remove);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

            if (parentNode != null) {
                int index = parentNode.getIndex(node);

                removeChildData(parentNode, node);
                parentNode.remove(node);

                ((DefaultTreeModel) tree.getModel()).nodesWereRemoved(
                        parentNode, new int[]{index}, new Object[]{node});
            }
            updateEditorText();
        }

        private void removeChildData(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode childNode) {
            if (parentNode instanceof RootNode) {
                CBORMetadataEx metadata = (CBORMetadataEx) parentNode.getUserObject();
                if (childNode instanceof KeyValueLeafNode) {
                    KeyValueLeafNode kvl = (KeyValueLeafNode) childNode;
                    metadata.removeItem((BigInteger) kvl.getKey());
                } else if (childNode instanceof MapNode) {
                    MapNode mapNode = (MapNode) childNode;
                    metadata.removeItem((BigInteger) mapNode.getKey());
                } else if (childNode instanceof ListNode) {
                    ListNode listNode = (ListNode) childNode;
                    metadata.removeItem((BigInteger) listNode.getKey());
                }
            } else if (parentNode instanceof MapNode) {
                CBORMetadataMapEx metadataMap = (CBORMetadataMapEx) parentNode.getUserObject();
                if (childNode instanceof KeyValueLeafNode) {
                    KeyValueLeafNode kvl = (KeyValueLeafNode) childNode;
                    metadataMap.removeItem((String) kvl.getKey());
                } else if (childNode instanceof MapNode) {
                    MapNode mapNode = (MapNode) childNode;
                    metadataMap.removeItem((BigInteger) mapNode.getKey());
                } else if (childNode instanceof ListNode) {
                    ListNode listNode = (ListNode) childNode;
                    metadataMap.removeItem((BigInteger) listNode.getKey());
                }
            } else if (parentNode instanceof ListNode) {
                int index = parentNode.getIndex(childNode);
                if (index == -1)
                    return;
                CBORMetadataListEx list = (CBORMetadataListEx) parentNode.getUserObject();
                list.removeItemAt(index);
            }
        }

        @Override
        public boolean isDumbAware() {
            return true;
        }
    }

    class EditAction extends BaseAction {
        public EditAction(DefaultMutableTreeNode node) {
            super(node, "Edit", "Edit", AllIcons.General.Inline_edit);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

            if (parentNode != null) {
                int index = parentNode.getIndex(node);

                updateValue(parentNode, node);

                ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
            }
            updateEditorText();
        }

        public void updateValue(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode node) {
            if(node instanceof KeyValueLeafNode) {
                KeyValueLeafNode kvl = (KeyValueLeafNode) node;
                KeyValueInputDialog dialog = new KeyValueInputDialog(project);
                dialog.setKey(String.valueOf(kvl.getKey()));
                dialog.setValue(String.valueOf(kvl.getValue()));
                dialog.setType(kvl.getType());
                dialog.enableEditMode();

                boolean ok = dialog.showAndGet();
                if (!ok) return;

                if(dialog.getKeyValueForm().getType() == DataType.Bytes) {
                    kvl.value = "0x" + HexUtil.encodeHexString((byte[])dialog.getKeyValueForm().getValue());
                } else {
                    kvl.value = dialog.getKeyValueForm().getValue();
                }
                kvl.refresh();

                Object parentObject = parentNode.getUserObject();
                if (parentObject instanceof CBORMetadataMapEx) {
                    ((CBORMetadataMapEx) parentObject).putValue(String.valueOf(kvl.getKey()), kvl.getValue());
                } else if(parentObject instanceof CBORMetadataEx) {
                    ((CBORMetadataEx) parentObject).putValue((BigInteger) kvl.getKey(), kvl.getValue());
                } else if (parentObject instanceof CBORMetadataListEx) {
                    ((CBORMetadataListEx) parentObject).addValue(kvl.getValue());
                }

                updateEditorText();
            } else if(node instanceof SingleValueLeafNode) {
                int index = parentNode.getIndex(node);
                if(index == -1)
                    return;

                SingleValueLeafNode svl = (SingleValueLeafNode) node;
                KeyValueInputDialog dialog = new KeyValueInputDialog(project);
                dialog.setValue(String.valueOf(svl.getValue()));
                dialog.setType(svl.getType());
                dialog.enableEditMode();
                dialog.enableListMode();

                boolean ok = dialog.showAndGet();
                if (!ok) return;
                if(dialog.getKeyValueForm().getType() == DataType.Bytes) {
                    svl.value = "0x" + HexUtil.encodeHexString((byte[])dialog.getKeyValueForm().getValue());
                } else {
                    svl.value = dialog.getKeyValueForm().getValue();
                }
                svl.refresh();

                Object parentObject = parentNode.getUserObject();
                if (parentObject instanceof CBORMetadataListEx) {
                    ((CBORMetadataListEx) parentObject).replaceValueAt(index, svl.getValue());
                }

                updateEditorText();
            }
        }

        @Override
        public boolean isDumbAware() {
            return true;
        }
    }

    class BuildTreeAction extends BaseAction {

        public BuildTreeAction(DefaultMutableTreeNode node) {
            super(node, "Reload From Editor", "Reload From Editor", AllIcons.General.ArrowLeft);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            String content = editorTf.getText();
            if(StringUtil.isEmpty(content))
                return;

            Metadata metadata = null;
            try {
                metadata = JsonNoSchemaToMetadataConverter.jsonToCborMetadata(content);
            } catch (JsonProcessingException jsonProcessingException) {
                MessageUtil.showMessage("Parsing error", "Metadata Json Parsing", true);
                return;
            }

            if(metadata == null) {
                MessageUtil.showMessage("Parsing error", "Metadata Json Parsing", true);
                return;
            }

            try {
                DefaultMutableTreeNode rootNode = MetadataModelToTreeModelBuilder.buildTree(metadata);
                treeModel.setRoot(rootNode);

            } catch (InvalidMetadataException ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public boolean isDumbAware() {
            return true;
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        RootNode root = new RootNode(CBORMetadataEx.create());
        //create the tree by passing in the root node
        treeModel = new DefaultTreeModel(root);
        tree = new Tree(treeModel);
        tree.setShowsRootHandles(true);

        editorTf = new JsonEditorTextField(project);
    }

    public static class RootNode extends DefaultMutableTreeNode {
        public RootNode(Metadata metadata) {
            super(metadata);
        }

        public CBORMetadata getCBORMetadata() {
            return (CBORMetadata) getUserObject();
        }

        @Override
        public String toString() {
            return "Metadata";
        }
    }

    public static class MapNode extends DefaultMutableTreeNode {
        private Object key;
        public MapNode(Object key, CBORMetadataMapEx map) {
            super(map);
            this.key = key;
        }

        public CBORMetadataEx getMap() {
            return (CBORMetadataEx) getUserObject();
        }

        public Object getKey() {
            return key;
        }

        @Override
        public String toString() {
            if(key != null) {
                return key + ": <Map>";
            } else {
                return "<Map>";
            }
        }
    }

    public static class ListNode extends DefaultMutableTreeNode {
        private Object key;
        public ListNode(Object key, CBORMetadataListEx list) {
            super(list);
            this.key = key;
        }

        public CBORMetadataListEx getList() {
            return (CBORMetadataListEx) getUserObject();
        }

        public Object getKey() {
            return key;
        }

        @Override
        public String toString() {
            if(key != null) {
                return key + ": [List]";
            } else {
                return "[List]";
            }
        }
    }

    public static class KeyValueLeafNode extends DefaultMutableTreeNode {
        DataType type;
        Object key;
        Object value;
        public KeyValueLeafNode(DataType type, Object key, Object value) {
            super(key + " : " + value);
            this.type = type;
            this.key = key;
            if(type == DataType.Bytes) {
                if(value != null) {
                    this.value = "0x" + HexUtil.encodeHexString((byte[]) value);
                    refresh();
                }
            } else {
                this.value = value;
            }
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public DataType getType() {
            return type;
        }

        public void refresh() {
            setUserObject(key + " : " + value);
        }
    }

    public static class SingleValueLeafNode extends DefaultMutableTreeNode {
        DataType type;
        Object value;
        public SingleValueLeafNode(DataType type, Object value) {
            super(value);
            this.type = type;
            if(type == DataType.Bytes) {
                if(value != null) {
                    this.value = "0x" + HexUtil.encodeHexString((byte[]) value);
                    refresh();
                }
            } else {
                this.value = value;
            }
        }

        public Object getValue() {
            return value;
        }

        public DataType getType() {
            return type;
        }

        public void refresh() {
            setUserObject(value);
        }
    }

}
