import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainCarreraCaballos {

    private static final int DISTANCIA_CARRERA = 1000;

    private static final int DISTANCIA_POTENCIADOR = 100;

    public static void main(String[] args) {

        int cantidadCaballos;
        Scanner scanner = new Scanner(System.in);

        //input usuario
        do{
            System.out.println("Ingrese la cantidad de caballos");
            cantidadCaballos = scanner.nextInt();
        }while (cantidadCaballos <= 0);

        // Genero los pozos en posiciones aleatorias
        Random random = new Random();
        List<Pozo> pozos = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if(pozos.isEmpty()){
                pozos.add(new Pozo(random.nextInt(DISTANCIA_CARRERA) + 1));
            }else{
                int posRandom = random.nextInt(DISTANCIA_CARRERA) + 1;
                if (pozos.get(i-1).getPosicion() != posRandom){
                    pozos.add(new Pozo(posRandom));
                }else{
                    i--;
                }
            }

        }

        List<Caballo> caballos = new ArrayList<>();
        for (int i = 1 ; i <= cantidadCaballos ; i++){
            caballos.add(new Caballo("Caballo-"+i,random.nextInt(3)+1,random.nextInt(3)+1,pozos));
        }

        //Muestro los caballos
        System.out.println("Caballos en la carrera:");
        for (Caballo c : caballos){
            System.out.println(c.getNombre());
        }

        //Muestro los pozos
        System.out.println("Pozos en la carrera:");
        for (Pozo pozo : pozos) {
            System.out.println("Pozo en la posición " + pozo.getPosicion());
        }

        Carrera carrera = new Carrera (caballos,pozos);
        carrera.iniciarcarrera();
    }

    private static class Carrera {

        List<Thread> hilosCaballos = new ArrayList<>();
        Semaphore semaforoCarrera;
        Thread hiloPotenciador;


        public Carrera(List<Caballo> caballos,List<Pozo> pozos){
            this.semaforoCarrera = new Semaphore(-2);
            Potenciador potenciador = new Potenciador();
            hiloPotenciador = new Thread(potenciador);
            hiloPotenciador.setDaemon(true);

            for(Caballo c : caballos){
                c.setPotenciador(potenciador);
                c.setSemaforo(semaforoCarrera);
                hilosCaballos.add(new Thread(c));
            }

            //Segun el # procesadores uso hilos daemon o no.
            int procesadores = Runtime.getRuntime().availableProcessors();
            if(caballos.size() > procesadores){
                for (Thread hc : hilosCaballos) {
                    hc.setDaemon(true);
                }
            }
        }

        private void iniciarcarrera() {

            hiloPotenciador.start();
            for(Thread hc : hilosCaballos){
                hc.start();
            }

            try{
                semaforoCarrera.acquire();
                finalizarCarrera();
            }catch(InterruptedException ignored){}
        }
        private void finalizarCarrera(){
            for(Thread hc : hilosCaballos){
                if(hc.isAlive() && !hc.isDaemon()){
                    hc.interrupt();
                }
            }
        }
    }

    private static class Potenciador implements Runnable{

        private AtomicInteger posicion;
        private AtomicBoolean disponible;

        public Potenciador(){
            posicion = new AtomicInteger(generarPosicionAleatoria());
            disponible = new AtomicBoolean(true);
        }

        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()){
                actualizarZona();
            }
        }

        private void actualizarZona(){
            try {
                Thread.sleep(15000);
                posicion.set(generarPosicionAleatoria());
                liberarPotenciador();
                System.out.println("Área potenciadora en " + posicion + " metros.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void potenciarCaballo(Caballo caballo){
            synchronized (this){
                caballo.bonusPotenciador();
            }
        }


        private static int generarPosicionAleatoria() {
            Random random = new Random();
            return random.nextInt(DISTANCIA_CARRERA) + 1;
        }

        public int getPosicion(){
            return posicion.get();
        }
        public boolean getDisponible(){
            return disponible.get();
        }

        private void ocuparPotenciador(){
            disponible.set(false);
        }

        private void liberarPotenciador(){
            disponible.set(true);
        }
    }

    private static class Pozo {

        private final int posicion;
        private AtomicBoolean caballoAtrapado;

        public Pozo(int posicion){
            this.posicion = posicion;
            caballoAtrapado= new AtomicBoolean(false);
        }

        public boolean getCaballoAtrapado() {
            return caballoAtrapado.get();
        }

        public  void atrapar() {
            caballoAtrapado.set(true);
        }

        public void rescatar() {
            caballoAtrapado.set(false);
        }

        public int getPosicion(){
            return posicion;
        }
    }

    private static class Caballo implements Runnable{

        Random random = new Random();
        private final String nombre;
        private final int velocidad;
        private final int resistencia;
        AtomicInteger posicion;
        List<Pozo> pozos;
        Semaphore semaforo;
        Potenciador potenciador;
        static AtomicInteger puesto = new AtomicInteger(0);

        public Caballo(String nombre,int velocidad,int resistencia,List<Pozo> pozos){
            this.nombre = nombre;
            this.velocidad = velocidad;
            this.resistencia = resistencia;
            this.posicion =  new AtomicInteger(0);
            this.pozos = pozos;
        }

        @Override
        public void run() {
            while(posicion.get() < DISTANCIA_CARRERA){
                if(Thread.currentThread().isInterrupted()){
                    System.out.println(nombre  + " no completó la carrera.");
                    System.exit(0);
                }
                avanzar();
                if(posicion.get()  >= DISTANCIA_CARRERA){
                    cruzarMeta();
                    break;
                }
                checkCaballoCaeEnpozo();
                checkCaballoPasaPorPotenciador();
                rescatarCaballo();
                descansar();
            }
        }

        public String getNombre(){
            return nombre;
        }

        public void setPotenciador(Potenciador potenciador){
            this.potenciador = potenciador;
        }

        public void setSemaforo(Semaphore semaforo){
            this.semaforo = semaforo;
        }

        private void avanzar(){
            posicion.getAndAdd(velocidad * random.nextInt(10) +1);
            System.out.printf(nombre+ " avanza " + posicion + " metros.\n");
        }

        public void bonusPotenciador(){
            posicion.getAndAdd(DISTANCIA_POTENCIADOR);
        }

        private void descansar(){
            int segundosASuspender = Math.abs(random.nextInt(5) + 1 -resistencia) * 1000;
            try {
                Thread.sleep(segundosASuspender);
            }catch (InterruptedException ignored){
                Thread.currentThread().interrupt();
            }
        }

        private void cruzarMeta(){
            puesto.incrementAndGet();
            System.out.println(nombre + " cruzó la linea de meta!! posicion: " + puesto);
            semaforo.release();
        }

        private void checkCaballoCaeEnpozo(){
            for (Pozo pozo : pozos) {
                if (posicion.get() >= pozo.getPosicion() && posicion.get() < pozo.getPosicion() + 10) {
                    synchronized (pozo) {
                        if (!pozo.getCaballoAtrapado()) {
                            pozo.atrapar();
                            System.out.println(nombre + " cayó en un pozo en la posición " + pozo.getPosicion() + ".");
                            esperarRescate(pozo);
                        }
                    }
                }
            }
        }

        private void esperarRescate(Pozo pozo){
            while (pozo.getCaballoAtrapado()){
                try{
                    pozo.wait();
                }catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println(nombre + " fue rescatado y continúa la carrera.");
        }

        private void rescatarCaballo() {
            for (Pozo pozo : pozos) {
                if (posicion.get() >= pozo.getPosicion() + 10 && pozo.getCaballoAtrapado()) {
                    synchronized (pozo) {
                        pozo.rescatar();
                        pozo.notify();
                        System.out.println(nombre + " rescató a un caballo atrapado en el pozo en la posición " + pozo.getPosicion() + ".");
                    }
                }
            }
        }

        private void checkCaballoPasaPorPotenciador() {
            if (posicion.get() >= potenciador.getPosicion() && posicion.get() < (potenciador.getPosicion() + 50)) {
                synchronized (potenciador) {
                    try {
                        while (!potenciador.getDisponible()) {
                            potenciador.wait();
                        }
                        potenciador.ocuparPotenciador();
                        Thread.sleep(7000);
                        posicion.getAndAdd(100);
                        System.out.println(nombre + " pasó por el área potenciadora en la posición " + potenciador.getPosicion() + " y avanzó " + DISTANCIA_POTENCIADOR +" metros, lleva " + posicion.get() + " metros.");
                    } catch (InterruptedException ignored) {
                    } finally {
                        potenciador.liberarPotenciador();
                        potenciador.notifyAll();
                    }
                }
            }
        }

    }
}