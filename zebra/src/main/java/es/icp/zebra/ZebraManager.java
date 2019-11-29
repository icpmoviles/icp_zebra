package es.icp.zebra;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;

import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.RFIDResults;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RegionInfo;
import com.zebra.rfid.api3.RegulatoryConfig;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.TagData;
import com.zebra.rfid.api3.TriggerInfo;
import com.zebra.scannercontrol.DCSSDKDefs;
import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.FirmwareUpdateEvent;
import com.zebra.scannercontrol.IDcsSdkApiDelegate;
import com.zebra.scannercontrol.SDKHandler;

import java.util.ArrayList;

import es.icp.logs.MyLog;
import es.icp.zebra.callbacks.ListenerConexion;
import es.icp.zebra.callbacks.ListenerLecturas;
import es.icp.zebra.core.Errores;
import es.icp.zebra.core.Lecturas;
import es.icp.zebra.enums.Conexion;
import es.icp.zebra.enums.Modo;

public class ZebraManager {

    //Instance of SDK Handler
    private static SDKHandler sdkHandler;
    private Context context;
    private ArrayList<ReaderDevice> readersListArray = null;
    private ReaderDevice readerDevice;
    private RFIDReader reader;
    private ListenerConexion listenerConexion;
    private ListenerLecturas listenerLecturas;
    private Conexion conexion;

    private boolean initBarcode = false;
    private boolean initRFID = false;

    private Modo modoLectura = Modo.RFID;

    private boolean intentoConfigRegion = true;

    private RfidEventsListener eventsListener;

    public ZebraManager (Context context) {
        this.context = context;
        MyLog.setDEBUG(true);
    }

    public ZebraManager (Context context, ListenerConexion listenerConexion){
        this(context);
        this.listenerConexion = listenerConexion;
    }


    public ArrayList<ReaderDevice> getLectores (){
        Readers readers = new Readers(context, ENUM_TRANSPORT.BLUETOOTH);
        readersListArray = new ArrayList<>();
        try {
            readersListArray = readers.GetAvailableRFIDReaderList();
            MyLog.d("Lectores encontrados (BT):" + readersListArray.size());
            for (ReaderDevice readerDevice : readersListArray) {
                MyLog.d("READER: " + readerDevice.getName() + "  " + readerDevice.getAddress());
            }

        } catch (InvalidUsageException e) {
            throwError("", Errores.LECTORES_NO_ENCONTRADOS, e.getVendorMessage() );
            MyLog.c(e.getInfo());
            e.printStackTrace();
        } finally {
            return readersListArray;
        }


    }

    public void showMostrarDialogListReader(){
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(context);
        builderSingle.setIcon(R.drawable.icon);
        builderSingle.setTitle("Seleccione lector");


        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(context, R.layout.item_list);
        for (ReaderDevice readerDevice : getLectores()){
            arrayAdapter.add(readerDevice.getName());
        }


        builderSingle.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                initReader(which, Modo.RFID);
            }
        });
        builderSingle.show();

    }
    //====================================================================================================
    // CONEXION / DESCONEXION
    //====================================================================================================


    public boolean initReader(int readerConectar, Modo modo){
        try{
            readerDevice = readersListArray.get(readerConectar);
            modoLectura = modo;
            switch (modo) {
                case BARCODE:
                    initBarcode = configureBarcode();
                    break;
                case RFID:
                    initRFID = configureRFID();
                    break;
            }
            if (initBarcode || initRFID) {
                statusConexion(Conexion.CONECTADO);
                MyLog.c("Reader conectado: " + reader.getHostName());
                return true;
            }else{
                return  false;
            }

        }catch (Exception e){
            statusConexion(Conexion.DESCONECTADO);
            throwError(reader.getHostName(), Errores.FALLO_INICIALIZACION,e.getMessage() );
            MyLog.e(e.toString());
            e.printStackTrace();
            return false;
        }

    }


    public boolean conectar (Modo modo){

        if (getLectores().size() == 0){
            throwError("", Errores.NO_CONECTADO, "No se han encontrado readers" );
            return false;
        }

        try {
            if (getLectores().size() > 1){
                showMostrarDialogListReader();
            }else{
                return  initReader(0, modo);
            }
            return true;
        } catch (Exception e) {
            statusConexion(Conexion.DESCONECTADO);
            throwError(reader.getHostName(), Errores.NO_CONECTADO,e.getMessage() );
            MyLog.e(e.toString());
            e.printStackTrace();
            return false;
        }
    }

    public boolean desconectar (){
        try {
            readerDevice.getRFIDReader().disconnect();
            statusConexion(readerDevice.getRFIDReader().isConnected() ? Conexion.CONECTADO : Conexion.DESCONECTADO);
            return !readerDevice.getRFIDReader().isConnected();
        } catch (InvalidUsageException e) {
            throwError(reader.getHostName(), Errores.FALLO_DESCONEXION,e.getMessage() );
            e.printStackTrace();
            return false;
        } catch (OperationFailureException e) {
            throwError(reader.getHostName(), Errores.FALLO_DESCONEXION,e.getMessage() );
            e.printStackTrace();
            return false;
        }
    }

    private boolean configurarRegion (){
        try {
            RegulatoryConfig regulatoryConfig = readerDevice.getRFIDReader().Config.getRegulatoryConfig();
            RegionInfo regionInfo = readerDevice.getRFIDReader().ReaderCapabilities.SupportedRegions.getRegionInfo(18);
            regulatoryConfig.setRegion(regionInfo.getRegionCode());
            RegionInfo selectedRegionInfo = readerDevice.getRFIDReader().Config.getRegionInfo(regionInfo);
            if (selectedRegionInfo.isHoppingConfigurable()) {
                regulatoryConfig.setIsHoppingOn(true);
            }
            String str = "865700,866300,866900,867500";
            regulatoryConfig.setEnabledChannels(str.split(","));
            MyLog.d("Region:" + regulatoryConfig.getRegion());
            MyLog.d("Hopping:" + regulatoryConfig.isHoppingon());
            MyLog.d("Enable Chanels:" + regulatoryConfig.getEnabledchannels());
            readerDevice.getRFIDReader().Config.setRegulatoryConfig(regulatoryConfig);
        } catch (InvalidUsageException e) {
            throwError(reader.getHostName(), Errores.FALLO_RFID_CONFIGURACION_REGION,e.getMessage() );
            e.printStackTrace();
            return false;
        } catch (OperationFailureException e) {
            throwError(reader.getHostName(), Errores.FALLO_RFID_CONFIGURACION_REGION,e.getMessage() );
            e.printStackTrace();
            return false;
        }
        return true;

    }

    public void modo (Modo m){

        if (readerDevice == null) {
            throwError(reader.getHostName(), Errores.NO_READER_CONECTADO,"" );
            return;
        }


        try {
            switch (m) {
                case RFID:
                    if (!initRFID){
                        initRFID = configureRFID();
                    }
                    readerDevice.getRFIDReader().Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, false);
                    break;

                case BARCODE:
                    readerDevice.getRFIDReader().Config.setTriggerMode(ENUM_TRIGGER_MODE.BARCODE_MODE, false);
                    if (!initBarcode) {
                        initBarcode = configureBarcode();
                    }

                    break;

            }
            modoLectura = m;
        } catch (InvalidUsageException ex) {
            throwError(reader.getHostName(), Errores.FALLO_CONFIGURACION_MODO,ex.getMessage() );
            ex.printStackTrace();

        } catch (OperationFailureException ex) {
            throwError(reader.getHostName(), Errores.FALLO_CONFIGURACION_MODO,ex.getMessage() );
            ex.printStackTrace();
        }


    }




    //==========================================================================================================
    //---  RFID ---
    //==========================================================================================================
    private boolean configureRFID (){
        //.................................................................................
        //configuracion RFID
        try {
            reader = readerDevice.getRFIDReader();
            reader.connect();
            reader.Events.addEventsListener(capturarEventosLecturas());


            reader.Events.setHandheldEvent(true); //para manejar los eventos
            reader.Events.setInventoryStartEvent(true);
            reader.Events.setInventoryStopEvent(true);
            reader.Events.setTagReadEvent(true);
            reader.Events.setReaderDisconnectEvent(true);
            reader.Events.setBatteryEvent(true);

            TriggerInfo triggerInfo = new TriggerInfo();
            triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
            triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
            reader.Config.setStartTrigger(triggerInfo.StartTrigger);
            reader.Config.setStopTrigger(triggerInfo.StopTrigger);
            return true;
            //.................................................................................
        } catch (InvalidUsageException ex) {
            throwError("SIN CONEXION", Errores.FALLO_RFID_CONFIGURACION, ex.getMessage() );
            ex.printStackTrace();
            return false;

        } catch (OperationFailureException ex) {
            throwError(reader.getHostName(), Errores.FALLO_RFID_CONFIGURACION,ex.getMessage() );
            if (intentoConfigRegion && ex.getResults() == RFIDResults.RFID_READER_REGION_NOT_CONFIGURED) {
                intentoConfigRegion = false;
                configurarRegion();
                configureRFID();
            }
            ex.printStackTrace();
            return false;

        }


    }

    private RfidEventsListener capturarEventosLecturas(){

            RfidEventsListener eventsListener = new RfidEventsListener() {
                @Override
                public void eventReadNotify (RfidReadEvents rfidReadEvents){

                    TagData[] myTags = reader.Actions.getReadTags(100);

                    final ArrayList<Lecturas> lecturas = new ArrayList<>();
                    if (myTags != null && myTags.length > 0) {

                        for (TagData tag : myTags) {
                            // MyLog.d("-> ID: " + tag.getTagID() );
                            //MyLog.d(tag.getMemoryBank().toString() + "-> ID: " + tag.getTagID() + " : " + tag.getMemoryBankData());
                            Lecturas l = new Lecturas(tag.getTagID());

                            if (tag != null && tag.LocationInfo != null) {
                                short dist = tag.LocationInfo.getRelativeDistance();
                                l.setDistancia(dist);
                            }
                            lecturas.add(l);
                        }

                        if (listenerLecturas != null && modoLectura == Modo.RFID) {
                            listenerLecturas.rfid(lecturas);
                        }
                    }
                }

                @Override
                public void eventStatusNotify (RfidStatusEvents rfidStatusEvents){
                    MyLog.d("Event status " + rfidStatusEvents.StatusEventData.getStatusEventType().toString());

                    if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
                        MyLog.d("Event status " + rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent().toString());
                        boolean triggerPresionado = rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED;

                        if (triggerPresionado) {
                            performInventory();
                        } else {
                            stopInventory();
                        }

                        listenerLecturas.presionar(triggerPresionado);
                    }


                    /*
                    GPI_EVENT
                    BUFFER_FULL_WARNING_EVENT
                    ANTENNA_EVENT
                    INVENTORY_START_EVENT
                    INVENTORY_STOP_EVENT
                    ACCESS_START_EVENT
                    ACCESS_STOP_EVENT
                    DISCONNECTION_EVENT
                    BUFFER_FULL_EVENT
                    NXP_EAS_ALARM_EVENT
                    READER_EXCEPTION_EVENT
                    HANDHELD_TRIGGER_EVENT
                    DEBUG_INFO_EVENT
                    TEMPERATURE_ALARM_EVENT
                    OPERATION_END_SUMMARY_EVENT
                    BATCH_MODE_EVENT
                    POWER_EVENT
                    BATTERY_EVENT
                    */


                }
            };
            return eventsListener;
        /*}     catch (InvalidUsageException e) {
                throwError(reader.getHostName(), Errores.FALLO_INICIALIZACION_RFID_EVENTS,e.getMessage() );
                e.printStackTrace();



        }*/


    }

    public void setOnListenerLecturas (ListenerLecturas listenerLecturas){
        this.listenerLecturas = listenerLecturas;
        try{
            if (eventsListener != null) { reader.Events.removeEventsListener(eventsListener); }
            reader.Events.addEventsListener(capturarEventosLecturas());
        }catch (InvalidUsageException e ){
            throwError(reader.getHostName(), Errores.FALLO_INICIALIZACION_RFID_EVENTS,e.getMessage() );
            e.printStackTrace();
        } catch (OperationFailureException e) {
            throwError(reader.getHostName(), Errores.FALLO_INICIALIZACION_RFID_EVENTS,e.getMessage() );
            e.printStackTrace();
        }

    }

    public void performInventory (){
        try {
            reader.Actions.Inventory.perform();
        } catch (InvalidUsageException e) {
            throwError(reader.getHostName(), Errores.START_REALIZAR_INVENARIO ,e.getMessage() );
            e.printStackTrace();
        } catch (OperationFailureException e) {
            throwError(reader.getHostName(), Errores.START_REALIZAR_INVENARIO ,e.getMessage() );
            e.printStackTrace();
        }
    }

    public void stopInventory (){
        try {
            reader.Actions.Inventory.stop();
        } catch (InvalidUsageException e) {
            throwError(reader.getHostName(), Errores.STOP_REALIZAR_INVENARIO ,e.getMessage() );
            e.printStackTrace();
        } catch (OperationFailureException e) {
            throwError(reader.getHostName(), Errores.STOP_REALIZAR_INVENARIO ,e.getMessage() );

            e.printStackTrace();
        }
    }

    public void startLocalizarTag (String tag){

        try {
            reader.Actions.TagLocationing.Perform(tag, null, null);
        } catch (InvalidUsageException e) {
            throwError(reader.getHostName(), Errores.START_LOCATION_TAG ,e.getMessage() );
            e.printStackTrace();
        } catch (OperationFailureException e) {
            throwError(reader.getHostName(), Errores.START_LOCATION_TAG ,e.getMessage() );
            e.printStackTrace();
        }
    }

    public void stopLocalizarTag (){
        //    this.rfidHandler.stopLocalizarTag();
        try {

            reader.Actions.TagLocationing.Stop();
        } catch (InvalidUsageException e) {
            throwError(reader.getHostName(), Errores.STOP_LOCATION_TAG ,e.getMessage() );
            e.printStackTrace();
        } catch (OperationFailureException e) {
            throwError(reader.getHostName(), Errores.STOP_LOCATION_TAG ,e.getMessage() );
            e.printStackTrace();
        }
    }




    //==========================================================================================================
    //---  BARCODE ---
    //==========================================================================================================
    private boolean configureBarcode (){
        try {
            //configuracion BARCODE
            sdkHandler = new SDKHandler(context);
            sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL);
            sdkHandler.dcssdkEnableAvailableScannersDetection(true);
            initializeDcsSdkWithAppSettings();
            sdkHandler.dcssdkSetDelegate(new IDcsSdkApiDelegate() {
                @Override
                public void dcssdkEventScannerAppeared (DCSScannerInfo dcsScannerInfo){
                    MyLog.d("TEST");
                }

                @Override
                public void dcssdkEventScannerDisappeared (int i){
                    MyLog.d("TEST");
                }

                @Override
                public void dcssdkEventCommunicationSessionEstablished (DCSScannerInfo dcsScannerInfo){
                    MyLog.d("TEST");
                }

                @Override
                public void dcssdkEventCommunicationSessionTerminated (int i){
                    MyLog.d("TEST");
                }

                @Override
                public void dcssdkEventBarcode (final byte[] bytes, int i, int i1){
                    MyLog.d("BARCODE_HANDLER :" + new String(bytes));

                    if (listenerLecturas != null && modoLectura == Modo.BARCODE) {
                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run (){
                                listenerLecturas.barcode(new String(bytes));
                            }
                        });

                    }

                }

                @Override
                public void dcssdkEventImage (byte[] bytes, int i){
                    MyLog.d("TEST");
                }

                @Override
                public void dcssdkEventVideo (byte[] bytes, int i){
                    MyLog.d("TEST");
                }

                @Override
                public void dcssdkEventBinaryData (byte[] bytes, int i){
                    MyLog.d("TEST");
                }

                @Override
                public void dcssdkEventFirmwareUpdate (FirmwareUpdateEvent firmwareUpdateEvent){
                    MyLog.d("TEST");
                }

                @Override
                public void dcssdkEventAuxScannerAppeared (DCSScannerInfo dcsScannerInfo, DCSScannerInfo dcsScannerInfo1){
                    MyLog.d("TEST");
                }
            });
            sdkHandler.dcssdkEstablishCommunicationSession(1);
            //.................................................................................
            return true;
        } catch (Exception e) {
            throwError(reader.getHostName(), Errores.FALLO_BARCODE_CONFIGURACION ,e.getMessage() );
            MyLog.e(e.toString());
            e.printStackTrace();
            return false;
        }

    }

    public void initializeDcsSdkWithAppSettings (){
        try{
            //capturamos los eventos que se produzcan al leer por medio del BARCODE
            int notifications_mask = 0;
            notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value;
            notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value;
            notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value;
            sdkHandler.dcssdkSubsribeForEvents(notifications_mask);
        }catch (Exception ex){
            throwError(reader.getHostName(), Errores.FALLO_INICIALIZACION_BARCODE_EVENTS,ex.getMessage() );
        }

    }


    //==========================================================================================================
    //---  CALLBACKS ---
    //==========================================================================================================
    private void throwError(String readerNombre, int codigoError, String mensaje ){
        if (listenerLecturas != null) listenerLecturas.error(readerNombre, codigoError, mensaje);
    }

    private void statusConexion (Conexion conexion){
        if (listenerConexion != null) {listenerConexion.status(0, conexion);}
    }

}
