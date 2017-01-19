// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.app.interfacetab

import java.awt.{ Frame, SystemColor }
import java.awt.event.{ ActionEvent, ActionListener, MouseAdapter, MouseEvent }
import java.util.{ HashSet => JHashSet }
import javax.swing.{ JMenuItem, JPopupMenu, JButton, ButtonGroup, JToggleButton,
  AbstractAction, Action, ImageIcon }

import scala.collection.mutable

import org.nlogo.api.Editable
import org.nlogo.app.common.{ Events => AppEvents }
import org.nlogo.core.I18N
import org.nlogo.swing.{ ToolBar, ToolBarActionButton, ToolBarToggleButton }
import org.nlogo.window.{ EditDialogFactoryInterface, Events => WindowEvents,
  GUIWorkspace, JobWidget, Widget, WidgetInfo }

class InterfaceToolBar(wPanel: WidgetPanel,
                       workspace: GUIWorkspace,
                       widgetInfos: List[WidgetInfo],
                       frame: Frame,
                       dialogFactory: EditDialogFactoryInterface) extends ToolBar
  with WidgetCreator
  with WindowEvents.WidgetForegroundedEvent.Handler
  with WindowEvents.WidgetRemovedEvent.Handler
  with WindowEvents.WidgetAddedEvent.Handler
  with AppEvents.WidgetSelectedEvent.Handler
  with WindowEvents.LoadBeginEvent.Handler
  with WindowEvents.EditWidgetEvent.Handler
  with ActionListener {

  private val selectedObjects = new mutable.HashSet[Widget]
  private val editAction = new EditAction
  private val editButton = new ToolBarActionButton(editAction)
  private val addAction = new AddAction
  private val addButton = new AddButton
  private val group = new ButtonGroup
  private val noneButton = new JToggleButton
  private val deleteAction = new DeleteAction
  private val deleteButton = new ToolBarActionButton(deleteAction)
  private val widgetMenu = new WidgetMenu

  wPanel.setWidgetCreator(this)
  // on Macs we want the window background but not on other systems
  if(System.getProperty("os.name").startsWith("Mac")) {
    setOpaque(true)
    setBackground(SystemColor.window)
  }

  var editTarget: Option[Editable] = None

  editButton.setToolTipText(I18N.gui.get("tabs.run.editButton.tooltip"))
  addButton.setToolTipText(I18N.gui.get("tabs.run.addButton.tooltip"))
  deleteButton.setToolTipText(I18N.gui.get("tabs.run.deleteButton.tooltip"))
  widgetMenu.setToolTipText(I18N.gui.get("tabs.run.widgets.tooltip"))

  class EditAction extends AbstractAction(I18N.gui.get("tabs.run.editButton")) {
    putValue(Action.SMALL_ICON, new ImageIcon(classOf[InterfaceToolBar].getResource("/images/edit.gif")))
    def actionPerformed(e: ActionEvent) {
      new WindowEvents.EditWidgetEvent(null).raise(InterfaceToolBar.this)
    }
  }

  class AddAction extends AbstractAction(I18N.gui.get("tabs.run.addButton")) {
    putValue(Action.SMALL_ICON, new ImageIcon(classOf[InterfaceToolBar].getResource("/images/add.gif")))
    def actionPerformed(e: ActionEvent) { }
  }

  // -------[WidgetPanel]------------[ToolBar]
  //  |-<mouse>--->|
  //               |
  //               |----<createWidgets>->|
  //                                     |
  //               |<---<makeWidget>-----|
  //               |-------------------->|
  //                                     |
  //               |<---<revalidate>-----|
  //               |-------------------->|
  //                                     |
  //               |<--------------------|
  //               |
  // |<------------|
  def createWidgets(widgetPanel: WidgetPanel, x: Int, y: Int): Unit = {
    if (! noneButton.isSelected) {
      noneButton.setSelected(true)
      widgetMenu.getSelectedWidget match {
        case c: org.nlogo.window.ControlWidget =>
          val setupButton = org.nlogo.core.Button(Some("setup"), 0, 0, 0, 0)
          val setupWidget = widgetPanel.makeWidget(setupButton)
          val goOnceButton = org.nlogo.core.Button(Some("go"), 0, 0, 0, 0)
          val goOnceWidget = widgetPanel.makeWidget(goOnceButton)
          val foreverButton = org.nlogo.core.Button(Some("go"), 0, 0, 0, 0, forever = true)
          val foreverWidget = widgetPanel.makeWidget(foreverButton)
          WidgetActions.addWidget(widgetPanel, setupWidget, x, y)
          WidgetActions.addWidget(widgetPanel, goOnceWidget, x + 76, y)
          WidgetActions.addWidget(widgetPanel, foreverWidget, x + 36, y + 43)
          widgetPanel.revalidate()
        case _ =>
          val widget = widgetPanel.makeWidget(widgetMenu.getSelectedWidget)
          WidgetActions.addWidget(widgetPanel, widget, x, y)
          widgetPanel.revalidate()
      }
    }
  }

  def handle(e: WindowEvents.EditWidgetEvent) {
    // this is to support the "Edit..." button in the view control strip - ST 7/18/03
    val targetOption = Option(e.widget).orElse {
      if (!editButton.isEnabled) return
      editTarget
    }.filter(wPanel.contains)
    for(target <- targetOption) {
      def suppress(b: Boolean) {
        target match {
          case w: JobWidget => w.suppressRecompiles(b)
          case _ =>
        }
      }
      suppress(true)
      editButton.setSelected(true)
      wPanel.editWidgetFinished(target, dialogFactory.canceled(frame, target))
      editButton.setSelected(false)
      suppress(false)
    }
  }

  class DeleteAction extends AbstractAction(I18N.gui.get("tabs.run.deleteButton")) {
    putValue(Action.SMALL_ICON, new ImageIcon(classOf[InterfaceToolBar].getResource("/images/delete.gif")))
    def actionPerformed(e: ActionEvent) {
      wPanel.deleteSelectedWidgets()
    }
  }

  private class AddButton extends ToolBarToggleButton(addAction) {
    // normally ToggleButtons when pressed again stay pressed, but we want it to pop back up if
    // pressed again; this variable is used to produce that behavior - ST 7/30/03, 2/22/07
    private var wasSelectedWhenMousePressed = false
    addMouseListener(new MouseAdapter {
      override def mousePressed(e: MouseEvent) { wasSelectedWhenMousePressed = isSelected }
    })
    addActionListener(new ActionListener {
      def actionPerformed(e: ActionEvent) { if (wasSelectedWhenMousePressed) noneButton.setSelected(true) }
    })
  }

  override def addControls() {
    Seq(editButton, deleteButton, addButton, widgetMenu).foreach(add)
    group.add(noneButton)
    group.add(addButton)
    noneButton.setSelected(true)
  }

  def handle(e: WindowEvents.LoadBeginEvent) {
    editAction.setEnabled(false)
    deleteAction.setEnabled(false)
    noneButton.setSelected(true)
    widgetMenu.setSelectedString(I18N.gui.get("tabs.run.widgets.button"))
  }

  def handle(e: WindowEvents.WidgetRemovedEvent) {
    val r = e.widget
    if(selectedObjects.contains(r)) {
      if(r.isInstanceOf[Editable] && editTarget.exists(_ == r.asInstanceOf[Editable])) {
        editTarget = None
        editAction.setEnabled(false)
      }
      selectedObjects.remove(r)
      deleteableObjects.remove(r)
      deleteAction.setEnabled(!deleteableObjects.isEmpty)
    }
  }

  def handle(e: WindowEvents.WidgetAddedEvent) {
    for(i <- widgetMenu.items) i.setEnabled(wPanel.canAddWidget(i.getText))
    widgetMenu.updateSelected()
  }

  private val deleteableObjects = new JHashSet[Widget]

  final def handle(e: AppEvents.WidgetSelectedEvent) {
    val w = e.widget
    if(wPanel.getWrapper(w).selected) {
      if(!selectedObjects.contains(w)) selectedObjects.add(w)
      if(w.deleteable && !deleteableObjects.contains(w)) deleteableObjects.add(w)
    }
    else {
      selectedObjects.remove(w)
      deleteableObjects.remove(w)
    }
    if(selectedObjects.isEmpty) {
      editTarget = None
      editAction.setEnabled(false)
    }
    deleteAction.setEnabled(!deleteableObjects.isEmpty)
  }

  def handle(e: WindowEvents.WidgetForegroundedEvent) {
    editTarget = Some(e.widget.getEditable).collect{case editable: Editable => editable}
    editAction.setEnabled(editTarget.isDefined)
  }

  def actionPerformed(e: ActionEvent) { addButton.setSelected(true) }

  def getItems: Array[JMenuItem] = widgetInfos.map(spec => new JMenuItem(spec.displayName, spec.icon)).toArray

  class WidgetMenu extends org.nlogo.swing.ToolBarComboBox(getItems) {
    def getSelectedWidget =
      widgetInfos.find(_.displayName == getSelectedItem.getText).get.coreWidget
    override def populate(menu: JPopupMenu) {
      super.populate(menu)
      for(i <- items) {
        i.setEnabled(wPanel.canAddWidget(i.getText))
        i.addActionListener(InterfaceToolBar.this)
      }
    }
  }
}
