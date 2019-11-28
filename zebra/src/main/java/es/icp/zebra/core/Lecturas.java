package es.icp.zebra.core;

public class Lecturas {
    protected String valor;
    protected short distancia;


    public Lecturas (String valor){
        this.valor = valor;
    }


    public String getValor (){
        return valor;
    }

    public void setValor (String valor){
        this.valor = valor;
    }

    public short getDistancia (){
        return distancia;
    }

    public void setDistancia (short distancia){
        this.distancia = distancia;
    }

    @Override
    public String toString (){
        return "Lecturas{" +
                "valor='" + valor + '\'' +
                '}';
    }
}
