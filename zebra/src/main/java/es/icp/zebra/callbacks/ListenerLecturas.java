package es.icp.zebra.callbacks;


import java.util.ArrayList;

import es.icp.zebra.core.Lecturas;

public interface ListenerLecturas {
    void rfid(ArrayList<Lecturas> tags);
    void barcode(String code);
    void presionar(boolean press);
    void error(String reader , int codeError, String e);



}
