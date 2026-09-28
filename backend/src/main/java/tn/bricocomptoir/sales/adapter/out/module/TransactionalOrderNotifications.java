package tn.bricocomptoir.sales.adapter.out.module;

import org.springframework.stereotype.Component;
import tn.bricocomptoir.sales.application.port.out.OrderNotifications;
import tn.bricocomptoir.sales.domain.OrderModels.Order;
import tn.bricocomptoir.notifications.application.port.in.NotificationOperations;

@Component
public class TransactionalOrderNotifications implements OrderNotifications {
    private final NotificationOperations notifications;
    public TransactionalOrderNotifications(NotificationOperations notifications) { this.notifications=notifications; }
    @Override public void changed(Order order,boolean created) {
        String email=order.snapshot().address().email();
        if(email!=null) {
            String status=switch(order.status()) {
                case CONFIRMED->"confirmée";case PREPARING->"en préparation";case SHIPPED->"expédiée";
                case DELIVERED->"livrée";case CANCELLED->"annulée";
            };
            StringBuilder text=new StringBuilder("Commande "+order.id()+" : "+status+".\n");
            for(var line:order.snapshot().items()) text.append(line.quantity()).append(" × ").append(line.label())
                .append(" — ").append(line.lineTotalTnd().toPlainString()).append(" TND\n");
            text.append("Livraison : ").append(order.snapshot().deliveryTnd().toPlainString()).append(" TND\n")
                .append("Total : ").append(order.snapshot().totalTnd().toPlainString()).append(" TND\nPaiement à la livraison.\n");
            notifications.enqueue(new NotificationOperations.Email("ORDER:"+order.id()+":"+order.status(),email,
                "BricoComptoir — commande "+status,text.toString(),null,null));
            notifications.associate("ORDER:"+order.id()+":"+order.status(),order.ownerScope());
        }
        if(created) notifications.orderCreated(order.id());
    }
}
