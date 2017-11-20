package org.optaplanner.openshift.employeerostering.gwtui.client.spot;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import elemental2.dom.CanvasRenderingContext2D;
import elemental2.dom.MouseEvent;
import org.optaplanner.openshift.employeerostering.gwtui.client.calendar.AbstractDrawable;
import org.optaplanner.openshift.employeerostering.gwtui.client.calendar.TimeRowDrawable;
import org.optaplanner.openshift.employeerostering.gwtui.client.calendar.TwoDayView;
import org.optaplanner.openshift.employeerostering.gwtui.client.canvas.CanvasUtils;
import org.optaplanner.openshift.employeerostering.gwtui.client.canvas.ColorUtils;
import org.optaplanner.openshift.employeerostering.gwtui.client.common.CommonUtils;
import org.optaplanner.openshift.employeerostering.gwtui.client.common.FailureShownRestCallback;
import org.optaplanner.openshift.employeerostering.gwtui.client.resources.css.CssResources;
import org.optaplanner.openshift.employeerostering.shared.employee.Employee;
import org.optaplanner.openshift.employeerostering.shared.employee.EmployeeRestServiceBuilder;
import org.optaplanner.openshift.employeerostering.shared.shift.ShiftRestServiceBuilder;
import org.optaplanner.openshift.employeerostering.shared.shift.view.ShiftView;

public class SpotDrawable extends AbstractDrawable implements TimeRowDrawable {

    TwoDayView view;
    SpotData data;
    int index;
    boolean isMouseOver;

    public SpotDrawable(TwoDayView view, SpotData data, int index) {
        this.view = view;
        this.data = data;
        this.index = index;
        this.isMouseOver = false;
        //ErrorPopup.show(this.toString());
    }

    @Override
    public double getLocalX() {
        double start = getStartTime().toEpochSecond(ZoneOffset.UTC) / 60;
        return start * view.getWidthPerMinute();
    }

    @Override
    public double getLocalY() {
        Integer cursorIndex = view.getCursorIndex(getGroupId());
        return (null != cursorIndex && cursorIndex > index) ? index * view.getGroupHeight() : (index + 1) * view.getGroupHeight();
    }

    @Override
    public void doDrawAt(CanvasRenderingContext2D g, double x, double y) {
        String color = getFillColor();
        CanvasUtils.setFillColor(g, color);

        double start = getStartTime().toEpochSecond(ZoneOffset.UTC) / 60;
        double end = getEndTime().toEpochSecond(ZoneOffset.UTC) / 60;
        double duration = end - start;

        CanvasUtils.drawCurvedRect(g, x, y, duration * view.getWidthPerMinute(), view.getGroupHeight());

        CanvasUtils.setFillColor(g, ColorUtils.getTextColor(color));

        String employee;
        if (null == data.getAssignedEmployee()) {
            employee = "Unassigned";
        } else {
            employee = data.getAssignedEmployee().getName();
            if (data.isLocked()) {
                employee += " (locked)";
            }
        }
        g.fillText(employee, x, y + view.getGroupHeight());
    }

    @Override
    public boolean onMouseMove(MouseEvent e, double x, double y) {
        view.preparePopup(this.toString());
        return true;
    }

    @Override
    public boolean onMouseEnter(MouseEvent e, double x, double y) {
        isMouseOver = true;
        return true;
    }

    @Override
    public boolean onMouseExit(MouseEvent e, double x, double y) {
        isMouseOver = false;
        return true;
    }

    @Override
    public boolean onMouseDown(MouseEvent mouseEvent, double x, double y) {
        EmployeeRestServiceBuilder.getEmployeeList(data.getShift().getTenantId(), new FailureShownRestCallback<List<Employee>>() {

            @Override
            public void onSuccess(List<Employee> employeeList) {
                CssResources.INSTANCE.errorpopup().ensureInjected();
                PopupPanel popup = new PopupPanel(false);
                popup.setGlassEnabled(true);
                // TODO: Change errorpopup to popup when edit functionality is merged
                popup.setStyleName(CssResources.INSTANCE.errorpopup().panel());

                VerticalPanel panel = new VerticalPanel();
                HorizontalPanel datafield = new HorizontalPanel();

                Label label = new Label("Is Locked");
                CheckBox checkbox = new CheckBox();
                checkbox.setValue(data.isLocked());
                datafield.add(label);
                datafield.add(checkbox);
                panel.add(datafield);

                datafield = new HorizontalPanel();
                label = new Label("Assigned Employee");
                ListBox listbox = new ListBox();
                employeeList.forEach((e) -> listbox.addItem(e.getName()));
                if (!data.isLocked()) {
                    listbox.setEnabled(false);
                }
                checkbox.addValueChangeHandler((v) -> listbox.setEnabled(v.getValue()));
                datafield.add(label);
                datafield.add(listbox);
                panel.add(datafield);

                datafield = new HorizontalPanel();
                Button confirm = new Button();
                // TODO: Use i18n value when edit functionality is merged
                confirm.setText("Confirm");
                confirm.addClickHandler((c) -> {
                    if (checkbox.getValue()) {
                        Employee employee = employeeList.stream().filter((e) -> e.getName().equals(listbox.getSelectedValue())).findFirst().get();
                        data.getShift().setLockedByUser(true);
                        data.getShift().setEmployee(employee);
                        ShiftView shiftView = new ShiftView(data.getShift());
                        popup.hide();
                        ShiftRestServiceBuilder.updateShift(data.getShift().getTenantId(), shiftView, new FailureShownRestCallback<Void>() {

                            @Override
                            public void onSuccess(Void result) {
                                view.getCalendar().forceUpdate();
                            }

                        });
                    } else {
                        data.getShift().setLockedByUser(false);
                        ShiftView shiftView = new ShiftView(data.getShift());
                        popup.hide();
                        ShiftRestServiceBuilder.updateShift(data.getShift().getTenantId(), shiftView, new FailureShownRestCallback<Void>() {

                            @Override
                            public void onSuccess(Void result) {
                                view.getCalendar().forceUpdate();
                            }

                        });
                    }

                });

                Button cancel = new Button();
                // TODO: Replace with i18n later
                cancel.setText("Cancel");
                cancel.addClickHandler((e) -> popup.hide());

                datafield.add(confirm);
                datafield.add(cancel);
                panel.add(datafield);

                popup.setWidget(panel);
                popup.center();
            }
        });

        return true;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public String getGroupId() {
        return data.getGroupId();
    }

    @Override
    public LocalDateTime getStartTime() {
        return data.getStartTime();
    }

    @Override
    public LocalDateTime getEndTime() {
        return data.getEndTime();
    }

    @Override
    public void doDraw(CanvasRenderingContext2D g) {
        String color = getFillColor();
        CanvasUtils.setFillColor(g, color);

        double start = getStartTime().toEpochSecond(ZoneOffset.UTC) / 60;
        double end = getEndTime().toEpochSecond(ZoneOffset.UTC) / 60;
        double duration = end - start;

        CanvasUtils.drawCurvedRect(g, getLocalX(), getLocalY(), duration * view.getWidthPerMinute(), view.getGroupHeight());

        CanvasUtils.setFillColor(g, ColorUtils.getTextColor(color));

        String employee;
        if (null == data.getAssignedEmployee()) {
            employee = "Unassigned";
        } else {
            employee = data.getAssignedEmployee().getName();
            if (data.isLocked()) {
                employee += " (locked)";
            }
        }
        g.fillText(employee, getLocalX(), getLocalY() + view.getGroupHeight());

        if (view.getGlobalMouseX() >= getGlobalX() && view.getGlobalMouseX() <= getGlobalX() + view.getWidthPerMinute() * duration && view.getGlobalMouseY() >= getGlobalY() && view.getGlobalMouseY() <= getGlobalY() + view.getGroupHeight()) {
            view.preparePopup(this.toString());

        }
    }

    public String toString() {
        StringBuilder out = new StringBuilder(data.getSpot().getName());
        out.append(' ');
        out.append(CommonUtils.pad(getStartTime().getHour() + "", 2));
        out.append(':');
        out.append(CommonUtils.pad(getStartTime().getMinute() + "", 2));
        out.append('-');
        out.append(CommonUtils.pad(getEndTime().getHour() + "", 2));
        out.append(':');
        out.append(CommonUtils.pad(getEndTime().getMinute() + "", 2));
        out.append(" -- ");
        String employee;
        if (null == data.getAssignedEmployee()) {
            employee = "no one";
        } else {
            employee = data.getAssignedEmployee().getName();
            if (data.isLocked()) {
                employee += " (locked)";
            }
        }
        out.append("Assigned to ");
        out.append(employee);
        return out.toString();
    }

    private String getFillColor() {
        if (isMouseOver) {
            return ColorUtils.brighten("#000000");
        }
        return "#000000";
    }

}
