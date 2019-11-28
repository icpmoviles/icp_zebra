package es.icp.zebra.callbacks;

import es.icp.zebra.enums.Conexion;

public interface ListenerConexion {
    void status(int code, Conexion estado);
}
