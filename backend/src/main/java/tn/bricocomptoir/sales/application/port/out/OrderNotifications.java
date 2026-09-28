package tn.bricocomptoir.sales.application.port.out;

import tn.bricocomptoir.sales.domain.OrderModels.Order;
public interface OrderNotifications {
    void changed(Order order,boolean created);
}
