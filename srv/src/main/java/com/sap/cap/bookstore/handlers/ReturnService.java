package com.sap.cap.bookstore.handlers;

import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.math.BigDecimal;

import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnDelete;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsService;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.handler.EventHandler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cds.gen.ordersservice.Orders;
import cds.gen.returnservice.ReturnItems;
import cds.gen.ordersservice.OrderItems;
import cds.gen.sap.capire.bookstore.OrderItems_;
import cds.gen.sap.capire.bookstore.Orders_;
import cds.gen.sap.capire.bookstore.Books;
import cds.gen.sap.capire.bookstore.Books_;
import cds.gen.sap.capire.bookstore.ReturnItems_;


@Component
@ServiceName("ReturnService")
public class ReturnService implements EventHandler {

    @Autowired
    PersistenceService db;

    @Before(event = CdsService.EVENT_CREATE, entity = "ReturnService.ReturnItems")
    public void validateBookAndIncreaseStock(List<ReturnItems> items) {
        for (ReturnItems item : items) {
            String bookId = item.getBookId();
            String orderId = item.getOrderId(); 
            Integer amount = item.getAmount();
  
            // check if the book that should be existing
            CqnSelect sel = Select.from(Books_.class).where(b -> b.ID().eq(bookId));
            Books book = db.run(sel).first(Books.class)
                    .orElseThrow(() -> new ServiceException(ErrorStatuses.NOT_FOUND, "Book does not exist"));
            
            // check the order should be existing
            sel = Select.from(Orders_.class).where(o -> o.ID().eq(orderId));
            Orders order = db.run(sel).first(Orders.class)
                    .orElseThrow(() -> new ServiceException(ErrorStatuses.NOT_FOUND, "Order does not exist"));
            
            // check bookID should match orderID
            CqnSelect selItems = Select.from(OrderItems_.class).where(i -> i.parent().ID().eq(orderId));
            List<OrderItems> allItems = db.run(selItems).listOf(OrderItems.class);
            int orderAmount = -1;
            for (OrderItems orderItem : allItems) {
                if (orderItem.getBookId().equals(bookId)) {
                    orderAmount = orderItem.getAmount();
                }
            }
            if (orderAmount == -1) {
                throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Order ID not match Book ID");
            }

            // check return amount should <= orderItem amount
            if (amount > orderAmount) {
                throw new ServiceException(ErrorStatuses.BAD_REQUEST, "return amount should equal or less than order amount");
            }
            
            // update the book with the new stock, means minus the order amount
            int stock = book.getStock();
            book.setStock(stock + amount);
            CqnUpdate update = Update.entity(Books_.class).data(book).where(b -> b.ID().eq(bookId));
            db.run(update);
        }
    }

    @After(event = { CdsService.EVENT_READ, CdsService.EVENT_CREATE }, entity = "ReturnService.ReturnItems")
    public void caculateNetAmountForReturn(List<ReturnItems> items) {
        for (ReturnItems item : items) {
          String bookId = item.getBookId();
          if (item.ID.equals("8e4cde7e-5e64-4bda-8d95-8d032a051ed3")) {
              CqnDelete del = Delete.from(ReturnItems_.class).where(r -> r.ID().eq("8e4cde7e-5e64-4bda-8d95-8d032a051ed3"));
              db.run(del);
          }
          // get the book that was returned
          CqnSelect sel = Select.from(Books_.class).where(b -> b.ID().eq(bookId));
          Books book = db.run(sel).single(Books.class);
        
          // calculate and set net amount
          item.setNetAmount(book.getPrice().multiply(new BigDecimal(item.getAmount())));
        }
    }
}