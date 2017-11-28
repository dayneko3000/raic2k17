import model.ActionType;
import model.Facility;
import model.Game;
import model.Move;
import model.Player;
import model.TerrainType;
import model.Vehicle;
import model.VehicleType;
import model.VehicleUpdate;
import model.WeatherType;
import model.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static model.VehicleType.ARRV;
import static model.VehicleType.FIGHTER;
import static model.VehicleType.HELICOPTER;
import static model.VehicleType.IFV;
import static model.VehicleType.TANK;


@SuppressWarnings({"UnsecureRandomNumberGeneration", "FieldCanBeLocal", "unused", "OverlyLongMethod"})
public final class MyStrategy implements Strategy {
    private static final int AIR = 1, GROUND = 2;
    private final Map<Long, Vehicle> vehicleById = new HashMap<>();
    private final Map<Long, Integer> updateTickByVehicleId = new HashMap<>();
    int assignIndex = 0;
    private int orderY = 120;
    private Random random;
    private TerrainType[][] terrainTypeByCellXY;
    private WeatherType[][] weatherTypeByCellXY;
    private List<Facility> facilities;
    private Player me;
    private World world;
    private Game game;
    private Move move;
    private Queue<Consumer<Move>> groundDeq = new ArrayDeque<>();
    private Queue<Consumer<Move>> airDeq = new ArrayDeque<>();
    private Queue<Consumer<Move>> nuclearDeq = new ArrayDeque<>();
    private Queue<Consumer<Move>> facilityDeq = new ArrayDeque<>();
    private int ping = 25;
    private int net = 10;
    private Point groundVector = new Point(1, 0);
    private Point airVector = new Point(0, 1);
    private Point tankMass, arrvMass, helicopterMass, fighterMass, ifvMass, nearestGroundEnemy, nearestAirEnemy, groundMass, airMass;
    private int center = 120;
    private Point airPoint1 = new Point(225, 35), airPoint2 = new Point(230, 230);
    private Point groundPoint1 = new Point(40, orderY), groundPoint2 = new Point((int) center, (int) orderY), groundPoint3 = new Point(190, orderY);
    private boolean orderedHorizontalGround = false;
    private boolean orderedVerticalGround = false;
    private boolean scaledGround = false;
    private boolean assignedGround = false;
    private boolean shiftedGround = false;
    private boolean horizontaledGround = false;
    private boolean discaledVerticalGround = false;
    private boolean discaledHorizonalGround = false;
    private boolean startGround = false;
    private boolean airPoint1Ready = false;
    private VehicleType airPoint1Vehicle = null;
    private boolean airPoint2Ready = false;
    private boolean orderedVerticalAir = false;
    private boolean assignedAir = false;
    private boolean shiftedAir = false;
    private boolean horizontaledAir = false;
    private boolean discaledVerticalAir = false;
    private boolean discaledHorizonalAir = false;
    private boolean startAir = false;
    private boolean nuclearReadyGroundFlag = true;
    private boolean nuclearReadyAirFlag = true;
    private boolean nuclearReadyAir = false;
    private boolean nuclearReadyGround = false;
    private int nuclearGroup = AIR;
    private boolean fighterSelected = false;
    private int fighterSelectedTick = 0;
    private boolean init = false;
    private boolean battleScalingAir = false;
    private boolean battleRotatingAir = false;
    private boolean battleDiscalingAir = false;
    private boolean nuclearScaleAir = false;
    private boolean nuclearDiscaleAir = false;
    private boolean battleScalingGround = false;
    private boolean battleRotatingGround = false;
    private boolean battleDiscalingGround = false;
    private boolean nuclearScaleGround = false;
    private boolean nuclearDiscaleGround = false;
    private int selectedGroup = 0;
    private double factor = 1.1;
    private boolean orderV2 = false;

    /**
     * Основной метод стратегии, осуществляющий управление армией. Вызывается каждый тик.
     *
     * @param me    Информация о вашем игроке.
     * @param world Текущее состояние мира.
     * @param game  Различные игровые константы.
     * @param move  Результатом работы метода является изменение полей данного объекта.
     */
    @Override
    public void move(Player me, World world, Game game, Move move) {
        initializeStrategy(world, game);
        initializeTick(me, world, game, move);

        if (me.getRemainingActionCooldownTicks() > 0) {
            return;
        }

        if (executeDelayedMove()) {
            return;
        }

        readyAir();
        readyGround();
        nuclearAttack();
        nuclearReady();

        if (!nuclearEnemyAvoid()) {
            if (startAir && airDeq.size() == 0 && ping()) {
                if (!startGround) {
                    airDeq.add(selectGroup(AIR));
                }
                goAir();
            }
            if (startGround && groundDeq.size() == 0 && ping()) {
                if (!startAir) {
                    airDeq.add(selectGroup(GROUND));
                }
//                buildVehicles();
                goGround();
//                assignIndex++;
//                assignIndex %= 25;
//                if (assignIndex == 0) {
//                    assignNewVehicles();
//                }
            }
        }
        executeDelayedMove();
    }

    private boolean ping() {
        return world.getTickIndex() % ping == 0;
    }

    /**
     * Инциализируем стратегию.
     * <p>
     * Для этих целей обычно можно использовать конструктор, однако в данном случае мы хотим инициализировать генератор
     * случайных чисел значением, полученным от симулятора игры.
     */
    private void initializeStrategy(World world, Game game) {
        if (random == null) {
            random = new Random(game.getRandomSeed());

            terrainTypeByCellXY = world.getTerrainByCellXY();
            weatherTypeByCellXY = world.getWeatherByCellXY();
        }
    }

    /**
     * Сохраняем все входные данные в полях класса для упрощения доступа к ним, а также актуализируем сведения о каждой
     * технике и времени последнего изменения её состояния.
     */
    private void initializeTick(Player me, World world, Game game, Move move) {
        this.me = me;
        this.world = world;
        this.game = game;
        this.move = move;

        for (Vehicle vehicle : world.getNewVehicles()) {
            vehicleById.put(vehicle.getId(), vehicle);
            if (vehicle.getPlayerId() == me.getId()) {
                updateTickByVehicleId.put(vehicle.getId(), world.getTickIndex());
            }
        }
        facilities = Arrays.stream(world.getFacilities()).collect(Collectors.toList());

        for (VehicleUpdate vehicleUpdate : world.getVehicleUpdates()) {
            long vehicleId = vehicleUpdate.getId();
            Vehicle vehicle = vehicleById.get(vehicleId);

            if (vehicleUpdate.getDurability() == 0) {
                vehicleById.remove(vehicleId);
                updateTickByVehicleId.remove(vehicleId);
            } else {
                if (vehicleUpdate.getX() != vehicle.getX() || vehicleUpdate.getY() != vehicle.getY() || vehicleUpdate.getGroups().length != vehicle.getGroups().length) {
                    vehicleById.put(vehicleId, new Vehicle(vehicleById.get(vehicleId), vehicleUpdate));
                    updateTickByVehicleId.put(vehicleId, world.getTickIndex());
                }
            }
        }


        tankMass = getMassOfVehicle(Ownership.ALLY, TANK);
        helicopterMass = getMassOfVehicle(Ownership.ALLY, HELICOPTER);
        ifvMass = getMassOfVehicle(Ownership.ALLY, IFV);
        fighterMass = getMassOfVehicle(Ownership.ALLY, FIGHTER);
        arrvMass = getMassOfVehicle(Ownership.ALLY, ARRV);
        groundMass = getMassOfVehicle(Ownership.ALLY, TANK, ARRV);
        airMass = getMassOfVehicle(Ownership.ALLY, HELICOPTER, FIGHTER);

        if (!init) {
            if (tankMass.getX() > world.getWidth() / 2) {
                orderY = (int) world.getHeight() - 100;
                airPoint1 = new Point(orderY, orderY);
                airPoint2 = new Point(world.getWidth() - 200, orderY);
                groundPoint1 = new Point(world.getWidth() - 40, orderY);
                groundPoint2 = new Point(world.getWidth() - 120, orderY);
                groundPoint3 = new Point(world.getWidth() - 190, orderY);

                groundVector = new Point(world.getWidth() / 2 - tankMass.x, 0);
                airVector = new Point(world.getWidth() / 2 - tankMass.x, 0);
            }
            setGroups();
            init = true;
        }

        nearestGroundEnemy = null;
        nearestAirEnemy = null;
        final double[] distance = {99999, 99999};
        streamVehicles(
                Ownership.ENEMY
        ).forEach((x) ->
        {
            if (distance[0] > distance(groundMass, new Point((int) x.getX(), (int) x.getY()))) {
                distance[0] = distance(groundMass, new Point((int) x.getX(), (int) x.getY()));
                nearestGroundEnemy = new Point((int) x.getX(), (int) x.getY());
            }
            if (distance[1] > distance(airMass, new Point((int) x.getX(), (int) x.getY()))) {
                distance[1] = distance(airMass, new Point((int) x.getX(), (int) x.getY()));
                nearestAirEnemy = new Point((int) x.getX(), (int) x.getY());
            }
        });

        if (nearestGroundEnemy == null) {
            distance[0] = 99999;
            streamVehicles(
                    Ownership.ENEMY
            ).forEach((x) ->
            {
                if (distance[0] > distance(groundMass, new Point((int) x.getX(), (int) x.getY()))) {
                    distance[0] = distance(groundMass, new Point((int) x.getX(), (int) x.getY()));
                    nearestGroundEnemy = new Point((int) x.getX(), (int) x.getY());
                }
            });
        }
    }

    Consumer<Move> getMove() {
        Consumer<Move> delayedMove = nuclearDeq.poll();
        if (delayedMove != null) {
            return delayedMove;
        }

        if (startAir && startGround) {
            if (selectedGroup == AIR) {
                if (canExecAir()) {
                    return airDeq.poll();
                } else {
                    if (canExecGround()) {
                        return selectGroup(GROUND);
                    }
                    return null;
                }
            } else {
                if (canExecGround()) {
                    return groundDeq.poll();
                } else {
                    if (canExecAir()) {
                        return selectGroup(AIR);
                    }
                    return null;
                }
            }
        } else {
            if (canExecAir()) {
                if (selectedGroup == AIR) {
                    return airDeq.poll();
                } else {
                    return selectGroup(AIR);
                }
            } else {
                if (canExecGround()) {
                    if (selectedGroup == GROUND) {
                        return groundDeq.poll();
                    } else {
                        return selectGroup(GROUND);
                    }
                }
            }
        }
        return facilityDeq.poll();
    }

    /**
     * Достаём отложенное действие из очереди и выполняем его.
     *
     * @return Возвращает {@code true}, если и только если отложенное действие было найдено и выполнено.
     */
    private boolean executeDelayedMove() {
        Consumer<Move> delayedMove = getMove();

        if (delayedMove == null && airDeq.size() == 0 && groundDeq.size() == 0) {
            return false;
        }

        if (delayedMove != null) {
            delayedMove.accept(move);
        }
        return true;
    }

    private boolean canExecAir() {
        if (airDeq.size() == 0 || (nuclearGroup == AIR && fireInTheHole(me))) {
            return false;
        }

        if (nuclearScaleAir) {
            if (!fireInTheHole(world.getOpponentPlayer())) {
                nuclearScaleAir = false;
                return true;
            }
            return false;
        }

        if (nuclearDiscaleAir || battleScalingAir || battleRotatingAir || battleDiscalingAir || nuclearReadyAir) {
            if (enough(AIR)) {
                if (nuclearDiscaleAir) {
                    nuclearDiscaleAir = false;
                    return true;
                }
                if (battleScalingAir) {
                    battleScalingAir = false;
                    return true;
                }
                if (battleRotatingAir) {
                    battleRotatingAir = false;
                    return true;
                }
                if (battleDiscalingAir) {
                    battleDiscalingAir = false;
                    return true;
                }
                if (nuclearReadyAir) {
                    nuclearReadyAir = false;
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    private boolean canExecGround() {
        if (groundDeq.size() == 0 || (nuclearGroup == GROUND && fireInTheHole(me))) {
            return false;
        }

        if (nuclearScaleGround) {
            if (!fireInTheHole(world.getOpponentPlayer())) {
                nuclearScaleGround = false;
                return true;
            }
            return false;
        }

        if (nuclearDiscaleGround || battleScalingGround || battleRotatingGround || battleDiscalingGround || nuclearReadyGround) {
            if (enough(GROUND)) {
                if (nuclearDiscaleGround) {
                    nuclearDiscaleGround = false;
                    return true;
                }
                if (battleScalingGround) {
                    battleScalingGround = false;
                    return true;
                }
                if (battleRotatingGround) {
                    battleRotatingGround = false;
                    return true;
                }
                if (battleDiscalingGround) {
                    battleDiscalingGround = false;
                    return true;
                }
                if (nuclearReadyGround) {
                    nuclearReadyGround = false;
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    private double getSpeed(VehicleType type) {
        switch (type) {
            case ARRV:
                return game.getArrvSpeed();
            case FIGHTER:
                return game.getFighterSpeed();
            case HELICOPTER:
                return game.getHelicopterSpeed();
            case IFV:
                return game.getIfvSpeed();
            default:
                return game.getTankSpeed();
        }
    }

    private boolean fireInTheHole(Player p) {
        return p.getRemainingNuclearStrikeCooldownTicks() >= game.getBaseTacticalNuclearStrikeCooldown() - game.getTacticalNuclearStrikeDelay();
    }

    private boolean enough(int group) {
        return streamVehicles(Ownership.ALLY).allMatch(
                vehicle -> world.getTickIndex() - updateTickByVehicleId.get(vehicle.getId()) > 5 || vehicle.getGroups().length == 0 || vehicle.getGroups()[0] != group
        );
    }

    private void readyAir() {
        if (!airPoint1Ready) {
            Point p;
            Point minFighter = getMinVehicle(FIGHTER);
            Point minHelicopter = getMinVehicle(HELICOPTER);
            if (distance(minFighter, airPoint2) < distance(minHelicopter, airPoint2)) {
                selectVehicleType(airDeq, FIGHTER);
                p = getMinVehicle(FIGHTER);
                airPoint1Vehicle = FIGHTER;
            } else {
                selectVehicleType(airDeq, HELICOPTER);
                p = getMinVehicle(HELICOPTER);
                airPoint1Vehicle = HELICOPTER;
            }
            scale(airDeq, new Point(p.x - (airPoint2.x - p.x), p.y - (airPoint2.y - p.y)), 2);
            airPoint1Ready = true;
            return;
        }
        if (!airPoint2Ready && enough(AIR)) {
            Point p;
            if (airPoint1Vehicle == HELICOPTER) {
                selectVehicleType(airDeq, FIGHTER);
                p = getMinVehicle(FIGHTER);
                airPoint1Vehicle = FIGHTER;
            } else {
                selectVehicleType(airDeq, HELICOPTER);
                p = getMinVehicle(HELICOPTER);
                airPoint1Vehicle = HELICOPTER;
            }
            scale(airDeq, new Point(p.x - (airPoint1.x - p.x), p.y - (airPoint1.y - p.y)), 2);
            airPoint2Ready = true;
            return;
        }

        if (!orderedVerticalAir && enough(AIR)) {
            Point airCenter = new Point(airPoint2.x, (airPoint1.y + airPoint2.y) / 2);
            selectVehicleType(airDeq, FIGHTER);
            moveVertical(airDeq, fighterMass, airCenter, game.getFighterSpeed() * 0.6);
            selectVehicleType(airDeq, HELICOPTER);
            moveVertical(airDeq, helicopterMass, airCenter, game.getHelicopterSpeed() * 0.6);
            orderedVerticalAir = true;
            return;
        }

        if (!discaledVerticalAir && enough(AIR)) {
            int shift = 0;
            List<Integer> rowList = getRowList(HELICOPTER, FIGHTER);
            rowList.sort(Integer::compareTo);
            double center = rowList.get(5);
            for (int row : rowList) {
                selectRow(airDeq, row, AIR);
                deselectGroup(airDeq, GROUND);
                moveVector(airDeq, new Point(shift == 0 ? 2 : 0, (center - row) / 2), game.getTankSpeed() * 0.6);
                shift = ++shift % 2;
            }
            discaledVerticalAir = true;
            return;
        }

        if (!startAir) {
            if (enough(AIR)) {
                startAir = true;
            }
        }
    }

    private void readyGround() {
        if (!orderedHorizontalGround) {
            double ground1 = Math.max(Math.abs(groundPoint1.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint2.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint3.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground2 = Math.max(Math.abs(groundPoint1.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint3.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint2.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground3 = Math.max(Math.abs(groundPoint2.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint1.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint3.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground4 = Math.max(Math.abs(groundPoint2.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint3.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint1.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground5 = Math.max(Math.abs(groundPoint3.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint1.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint2.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground6 = Math.max(Math.abs(groundPoint3.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint2.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint1.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double groundMinDist = Math.min(Math.min(Math.min(Math.min(Math.min(ground1, ground2), ground3), ground4), ground5), ground6);
            if (ground1 == groundMinDist) {
                horizontalGroundMove(groundPoint1, groundPoint2, groundPoint3);
            }
            if (ground2 == groundMinDist) {
                horizontalGroundMove(groundPoint1, groundPoint3, groundPoint2);
            }
            if (ground3 == groundMinDist) {
                horizontalGroundMove(groundPoint2, groundPoint1, groundPoint3);
            }
            if (ground4 == groundMinDist) {
                horizontalGroundMove(groundPoint2, groundPoint3, groundPoint1);
            }
            if (ground5 == groundMinDist) {
                horizontalGroundMove(groundPoint3, groundPoint1, groundPoint2);
            }
            if (ground6 == groundMinDist) {
                horizontalGroundMove(groundPoint3, groundPoint2, groundPoint1);
            }
            orderedHorizontalGround = true;
            return;
        }
        if (!orderedVerticalGround && enough(GROUND)) {
            verticalGroundMove(groundPoint1, groundPoint2, groundPoint3);
            orderedVerticalGround = true;
            return;
        }

        if (!scaledGround && enough(GROUND)) {
            for (int row : getRowList(ARRV)) {
                selectRow(groundDeq, row, GROUND);
                deselectGroup(groundDeq, AIR);
                shiftVertical(groundDeq, (row - orderY) * 2);
            }
            scaledGround = true;
            return;
        }

        if (!shiftedGround && enough(GROUND)) {
            selectVehicleType(groundDeq, TANK);
            shiftVertical(groundDeq, 6);
            selectVehicleType(groundDeq, IFV);
            shiftVertical(groundDeq, -6);
            shiftedGround = true;
            return;
        }
        if (!horizontaledGround && enough(GROUND)) {
            selectVehicleType(groundDeq, TANK);
            moveHorizontal(groundDeq, tankMass, groundPoint2, game.getTankSpeed() * 0.6);
            selectVehicleType(groundDeq, IFV);
            moveHorizontal(groundDeq, ifvMass, groundPoint2, game.getIfvSpeed() * 0.6);
            selectVehicleType(groundDeq, ARRV);
            moveHorizontal(groundDeq, arrvMass, groundPoint2, game.getArrvSpeed() * 0.6);
            horizontaledGround = true;

            return;
        }

        if (!discaledVerticalGround && enough(GROUND)) {
            groundDeq.add(selectGroup(GROUND));
            scale(groundDeq, groundMass, 0.8);
            discaledVerticalGround = true;
            return;
        }

        if (!startGround && enough(GROUND)) {
            startGround = true;
        }
    }

    private Point getMinVehicle(VehicleType type) {
        final double[] x = {9999};
        final double[] y = {9999};
        streamVehicles(Ownership.ALLY, type).forEach((v) ->
        {
            if (v.getX() <= x[0]) {
                x[0] = v.getX();
            }
            if (v.getY() <= y[0]) {
                y[0] = v.getY();
            }
        });
        return new Point(x[0], y[0]);
    }

    private void setGroups() {
        for (VehicleType type : VehicleType.values()) {
            selectVehicleType(airDeq, type);
            assign(airDeq, type == HELICOPTER || type == FIGHTER ? AIR : GROUND);
        }
    }

    private void assign(Queue<Consumer<Move>> deq, int group) {
        deq.add((move) ->
        {
            move.setAction(ActionType.ASSIGN);
            move.setGroup(group);
        });
    }

    private void selectRow(Queue<Consumer<Move>> deq, int y, int group) {
        deq.add((move) ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setTop(y - 1);
            move.setBottom(y + 1);
            selectedGroup = group;
        });
    }

    private void selectColumn(Queue<Consumer<Move>> deq, int x, int group) {
        deq.add((move) ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setBottom(world.getHeight());
            move.setRight(x + 3);
            move.setLeft(x - 3);
            selectedGroup = group;
        });
    }

    private void deselectGroup(Queue<Consumer<Move>> deq, int group) {
        deq.add((move) ->
        {
            move.setAction(ActionType.DESELECT);
            move.setGroup(group);
        });
    }

    private void deselectVehicleType(Queue<Consumer<Move>> deq) {
        deq.add((move) ->
        {
            move.setAction(ActionType.DESELECT);
            move.setVehicleType(IFV);
        });
    }

    private List<Integer> getRowList(VehicleType... types) {
        Set<Integer> result = streamVehicles(Ownership.ALLY, types).map((v) -> (int) v.getY()).collect(Collectors.toSet());
        return new ArrayList<>(result);
    }

    private List<Integer> getColumnList(VehicleType... types) {
        Set<Integer> result = streamVehicles(Ownership.ALLY, types).map((v) -> (int) v.getX()).collect(Collectors.toSet());
        return new ArrayList<>(result);
    }

    private void verticalGroundMove(Point p1, Point p2, Point p3) {
        selectVehicleType(groundDeq, TANK);
        moveVertical(groundDeq, tankMass, p1, game.getTankSpeed() * 0.6);
        selectVehicleType(groundDeq, IFV);
        moveVertical(groundDeq, ifvMass, p2, game.getIfvSpeed() * 0.6);
        selectVehicleType(groundDeq, ARRV);
        moveVertical(groundDeq, arrvMass, p3, game.getArrvSpeed() * 0.6);
    }

    private void horizontalGroundMove(Point p1, Point p2, Point p3) {
        selectVehicleType(groundDeq, TANK);
        moveHorizontal(groundDeq, tankMass, p1, game.getTankSpeed() * 0.6);
        selectVehicleType(groundDeq, IFV);
        moveHorizontal(groundDeq, ifvMass, p2, game.getIfvSpeed() * 0.6);
        selectVehicleType(groundDeq, ARRV);
        moveHorizontal(groundDeq, arrvMass, p3, game.getArrvSpeed() * 0.6);
    }


    private void moveFromTo(Queue<Consumer<Move>> deq, Point from, Point to, Double maxSpeed) {
        deq.add(move ->
        {
            double edgeX = Math.min(Math.max(90, to.getX()), world.getWidth() - 90);
            double edgeY = Math.min(Math.max(90, to.getY()), world.getHeight() - 90);
            move.setAction(ActionType.MOVE);
            move.setX(edgeX - from.getX());

            move.setY(edgeY - from.getY());
            move.setMaxSpeed(maxSpeed);
        });
    }

    private void moveHorizontal(Queue<Consumer<Move>> deq, Point from, Point to, Double maxSpeed) {
        deq.add(move ->
        {
            move.setAction(ActionType.MOVE);
            move.setX(to.getX() - from.getX());
            if (maxSpeed != null) {
                if (Math.abs(to.getX() - from.getX()) > maxSpeed) {
                    move.setMaxSpeed(maxSpeed);
                } else {
                    move.setMaxSpeed(Math.abs(to.getX() - from.getX()));
                }
            }

        });
    }

    private void moveVertical(Queue<Consumer<Move>> deq, Point from, Point to, Double maxSpeed) {
        deq.add(move ->
        {
            move.setAction(ActionType.MOVE);
            move.setY(to.getY() - from.getY());
            if (maxSpeed != null) {
                move.setMaxSpeed(maxSpeed);
            }
        });
    }

    private void shiftVertical(Queue<Consumer<Move>> deq, int y) {
        deq.add(move ->
        {
            move.setAction(ActionType.MOVE);
            move.setY(y);
        });
    }

    private void scale(Queue<Consumer<Move>> deq, Point from, double factor) {
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(from.getX());
            move.setY(from.getY());
            move.setFactor(factor);
        });
    }

    private void scale(Queue<Consumer<Move>> deq, Point from, double factor, Integer group) {
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(from.getX());
            move.setY(from.getY());
            move.setFactor(factor);
            if (group == AIR) {
                nuclearReadyAir = true;
            } else {
                nuclearReadyGround = true;
            }
        });
    }

    private void scaleHorizontal(Queue<Consumer<Move>> deq, Point from, double factor) {
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(from.getX());
            move.setY(world.getHeight());
            move.setFactor(factor);
            move.setMaxSpeed(game.getHelicopterSpeed() * 0.6);
        });
    }

    private void scaleVertical(Queue<Consumer<Move>> deq, Point from, double factor) {
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setY(from.getY());
            move.setX(world.getWidth());
            move.setFactor(factor);
            move.setMaxSpeed(game.getHelicopterSpeed() * 0.6);
        });
    }

    private void nuclearEnemyAvoid(Queue<Consumer<Move>> deq, Point p, int group) {
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(10);
            if (group == AIR) {
                nuclearScaleAir = true;
            } else {
                nuclearScaleGround = true;
            }
        });
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(0.1);
            if (group == AIR) {
                nuclearDiscaleAir = true;
            } else {
                nuclearDiscaleGround = true;
            }
        });
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(1.1);
            if (group == AIR) {
                nuclearDiscaleAir = true;
                nuclearReadyAirFlag = false;
            } else {
                nuclearReadyGroundFlag = false;
                nuclearDiscaleGround = true;
            }
        });
    }

    private void rotateAround(Queue<Consumer<Move>> deq, Point p, double angle, double factor, int group) {
        deq.add(move ->
        {
            move.setAction(ActionType.ROTATE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setAngle(angle);
            if (group == AIR) {
                battleRotatingAir = true;
            } else {
                battleRotatingGround = true;
            }
        });
    }

    private double getDistance(Point p1, Point p2) {
        return Math.sqrt((p1.getX() - p2.getX()) * (p1.getX() - p2.getX()) + (p1.getY() - p2.getY()) * (p1.getY() - p2.getY()));
    }

    private void selectVehicleType(Queue<Consumer<Move>> deq, VehicleType vehicleType) {
        deq.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setBottom(world.getHeight());
            if (vehicleType != null) {
                move.setVehicleType(vehicleType);
            } else {
                selectedGroup = 0;
            }
        });
    }

    private double getAngleToEnemy(int group, Point p) {
        Point mass = group == AIR ? airMass : groundMass;
        Point attack = new Point(p.x - mass.x, p.y - mass.y);
        return getAngle(group == AIR ? airVector : groundVector, attack);
    }

    private double getAngle(Point a, Point b) {
        int sign = a.x * b.y - b.x * a.y > 0 ? 1 : -1;
        return sign * Math.acos((a.x * b.x + a.y * b.y) / (Math.sqrt(a.x * a.x + a.y * a.y) * Math.sqrt(b.x * b.x + b.y * b.y)));
    }

    private Point turnVector(Point v, double ang) {
        return new Point((v.x * Math.cos(ang) - v.y * Math.sin(ang)), (v.x * Math.sin(ang) + v.y * Math.cos(ang)));
    }

    private double distance(Point p1, Point p2) {
        return Math.sqrt((p1.getX() - p2.getX()) * (p1.getX() - p2.getX()) + (p1.getY() - p2.getY()) * (p1.getY() - p2.getY()));
    }

    private void moveVector(Queue<Consumer<Move>> deq, Point p, double maxSpeed) {
        deq.add(move ->
        {
            move.setAction(ActionType.MOVE);
            move.setX(p.x);
            move.setY(p.y);
            move.setMaxSpeed(maxSpeed);
        });
    }

    private Consumer<Move> selectGroup(int group) {
        return move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setBottom(world.getHeight());
            move.setGroup(group);
            selectedGroup = group;
        };
    }

    /**
     * Основная логика нашей стратегии.
     */
    private Point getTargetMass(Point p) {
        double x = 0;
        double y = 0;
        List<Vehicle> vList = streamVehicles(Ownership.ENEMY).collect(Collectors.toList());
        int count = 0;
        for (Vehicle v : vList) {
            if (v.getDistanceTo(p.x, p.y) < 100) {
                x += p.x;
                y += p.y;
                count++;
            }
        }
        return new Point(x / count, y / count);
    }

    public boolean nuclearEnemyAvoid() {
        if (!fireInTheHole(world.getOpponentPlayer())) {
            return false;
        }

        Point enemyNuclearSrtike = new Point(world.getOpponentPlayer().getNextNuclearStrikeX(), world.getOpponentPlayer().getNextNuclearStrikeY());
        if (distance(enemyNuclearSrtike, groundMass) < 20 + game.getTacticalNuclearStrikeRadius()) {
            nuclearEnemyAvoid(groundDeq, enemyNuclearSrtike, GROUND);
        }

        if (distance(enemyNuclearSrtike, airMass) < 20 + game.getTacticalNuclearStrikeRadius()) {
            nuclearEnemyAvoid(airDeq, enemyNuclearSrtike, AIR);
        }

        return true;
    }

    private Point getFacility(int group) {
        if (facilities == null) {
            return null;
        }
        Point mass = group == GROUND ? groundMass : airMass;
        Facility nearest = null;
        double dist = 99999;
        for (Facility f : facilities) {
            if (f.getOwnerPlayerId() != me.getId() && (distance(new Point(f.getLeft(), f.getTop()), mass) < dist)) {
                nearest = f;
                dist = distance(new Point(f.getLeft() + 20, f.getTop() + 20), mass);
            }
        }
        if (nearest == null) {
            return null;
        }
        return new Point(nearest.getLeft() + 20, nearest.getTop() + 20);
    }

    private void goGround() {
        if (getFacility(GROUND) == null || (isBigTarget(nearestGroundEnemy, 100) && distance(nearestGroundEnemy, groundMass) < 100)) {
            boolean bigTarget = isBigTarget(nearestGroundEnemy, 35);
            if (bigTarget) {
                Point targetMass = getTargetMass(nearestGroundEnemy);
                double angleToTurn = getAngleToEnemy(GROUND, targetMass);
                if (Math.abs(angleToTurn) > Math.PI / 10 && distance(groundMass, targetMass) > 40) {
                    rotateAround(groundDeq, groundMass, angleToTurn, factor, GROUND);
                    groundVector = turnVector(groundVector, angleToTurn);
                    return;
                }
            }
            if (!bigTarget) {
                moveFromTo(groundDeq, groundMass, nearestGroundEnemy, game.getTankSpeed() * 0.6);
            } else {
                double dist = distance(groundMass, nearestGroundEnemy);
                if (getEnemyAround(nearestGroundEnemy, game.getTacticalNuclearStrikeRadius() * 2) > streamVehicles(Ownership.ALLY).count() * 0.8 && distanceToTheEdge(groundMass) > 100) {
                    Point target = new Point(groundMass.getX() + (nearestGroundEnemy.getX() - groundMass.getX()) / dist * (dist - game.getFighterVisionRange() * 1.2 - game.getHelicopterSpeed() * 0.6 * game.getTacticalNuclearStrikeDelay()),
                            groundMass.getY() + (nearestGroundEnemy.getY() - groundMass.getY()) / dist * (dist - game.getFighterVisionRange() * 1.2 - game.getHelicopterSpeed() * 0.6 * game.getTacticalNuclearStrikeDelay()));
                    moveFromTo(groundDeq, groundMass, target, game.getTankSpeed() * 0.6);
                } else {
                    moveFromTo(groundDeq, groundMass, nearestGroundEnemy, game.getTankSpeed() * 0.6);
                }
            }
        } else {
            moveFromTo(groundDeq, groundMass, getFacility(GROUND), game.getTankSpeed() * 0.6);
        }
    }

    private double distanceToTheEdge(Point p) {
        return Math.min(Math.min(Math.min(p.x, world.getWidth() - p.x), p.y), world.getHeight() - p.y);
    }

    private int getGroupCount(int group) {
        return (int) streamVehicles(Ownership.ALLY).filter((v) -> v.getGroups()[0] == group).count();
    }

    private void nuclearReady() {
        if (startGround && world.getOpponentPlayer().getRemainingNuclearStrikeCooldownTicks() < 100 && (!nuclearReadyAirFlag || !nuclearReadyGroundFlag)) {
            if (!nuclearReadyAirFlag) {
                scale(airDeq, airMass, 1.25, AIR);
                nuclearReadyAirFlag = true;
            }
            if (!nuclearReadyGroundFlag) {
                scale(groundDeq, groundMass, 1.25, GROUND);
                nuclearReadyGroundFlag = true;
            }
        }
    }

    private void buildVehicles() {
        for (Facility f : facilities) {
            if (f.getOwnerPlayerId() == me.getId() && f.getVehicleType() != IFV && distance(new Point(f.getLeft() + 20, f.getTop() + 20), groundMass) > 100) {
                nuclearDeq.add(move ->
                {
                    move.setAction(ActionType.SETUP_VEHICLE_PRODUCTION);
                    move.setFacilityId(f.getId());
                    move.setVehicleType(IFV);
                });
            }
        }
    }

    private void assignNewVehicles() {
        selectVehicleType(facilityDeq, IFV);
        deselectGroup(facilityDeq, GROUND);
        moveVector(facilityDeq, new Point(0, 6), game.getIfvSpeed());
        facilityDeq.add(selectGroup(GROUND));
    }

    private void goAir() {
        if (getFacility(GROUND) != null) {
            if (distance(groundMass, nearestGroundEnemy) < 200) {
                airAttack(nearestGroundEnemy);
                return;
            }
            airSave();
            return;
        }

        if (distance(groundMass, nearestGroundEnemy) < 60 && isBigTarget(nearestGroundEnemy, 35)) {
            airSave();
        } else {
            airAttack(nearestAirEnemy);
        }
    }

    private void airSave() {
        if (distance(groundMass, airMass) < 30) {
            double angleToTurn = getAngle(airVector, groundVector);

            if (Math.abs(angleToTurn) > Math.PI / 10) {
                rotateAround(airDeq, airMass, angleToTurn, factor, AIR);
                airVector = turnVector(airVector, angleToTurn);
                return;
            }
        }
        moveFromTo(airDeq, airMass, groundMass, game.getHelicopterSpeed() * 0.6);
    }

    private void airAttack(Point p) {
        if (!isBigTarget(p, 35)) {
            moveFromTo(airDeq, airMass, p, game.getHelicopterSpeed() * 0.6);
        } else {
            double dist = distance(groundMass, p);
            double coeff;
            if (me.getRemainingNuclearStrikeCooldownTicks() > 100) {
                if (distance(airMass, groundMass) < 150 && world.getOpponentPlayer().getRemainingNuclearStrikeCooldownTicks() > 200) {
                    moveFromTo(airDeq, airMass, groundMass, game.getHelicopterSpeed() * 0.6);
                    return;
                }
                coeff = 1.2;
            } else {
                coeff = 0.6;
            }
            Point target = new Point(groundMass.getX() + (p.getX() - groundMass.getX()) / dist * (dist - game.getFighterVisionRange() * coeff - game.getHelicopterSpeed() * 0.6 * game.getTacticalNuclearStrikeDelay()),
                    groundMass.getY() + (p.getY() - groundMass.getY()) / dist * (dist - game.getFighterVisionRange() * coeff - game.getHelicopterSpeed() * 0.6 * game.getTacticalNuclearStrikeDelay()));

            double angleToTurn = getAngleToEnemy(AIR, p);

            if (Math.abs(angleToTurn) > Math.PI / 10 && distance(airMass, p) > 30) {
                rotateAround(airDeq, airMass, angleToTurn, factor, AIR);
                airVector = turnVector(airVector, angleToTurn);
                return;
            }

            moveFromTo(airDeq, new Point(airMass.getX(), airMass.getY()),
                    target,
                    game.getHelicopterSpeed() * 0.6);
        }
    }

    private int getEnemyAround(Point p, double radius) {
        int countEnemy = 0;
        List<Vehicle> vList = streamVehicles(Ownership.ENEMY).collect(Collectors.toList());
        for (Vehicle v : vList) {
            if (v.getDistanceTo(p.x, p.y) < radius) {
                countEnemy++;
            }
        }
        return countEnemy;
    }

    private boolean isBigTarget(Point p, int bigCount) {
        if (getEnemyAround(p, game.getTacticalNuclearStrikeRadius()) > bigCount) {
            return true;
        }
        return false;
    }

    private double getGroupAvarageHealth(int group) {
        double durability = 0;
        List<Vehicle> vList = streamVehicles(Ownership.ALLY).filter((v) -> v.getGroups()[0] == group).collect(Collectors.toList());
        for (Vehicle v : vList) {
            if (v.getDurability() < 70) {
                durability++;
            }
        }
        return durability / vList.size();
    }

    private boolean nuclearAttack() {
        if (fireInTheHole(world.getMyPlayer())) {
            return true;
        }
        if (me.getRemainingNuclearStrikeCooldownTicks() > 0) {
            return false;
        }
        int net = (int) game.getFighterVisionRange() * 2;
        int[][] enemys = new int[(int) world.getWidth() / net + 1][(int) world.getHeight() / net + 1];
        final boolean[] notSkip = {false};
        streamVehicles(Ownership.ENEMY).forEach(e -> enemys[(int) e.getX() / net][(int) e.getY() / net] = 1);
        streamVehicles(Ownership.ALLY).forEach(a ->
        {
            if (enemys[(int) (a.getX() / net)][(int) (a.getY() / net)] == 1) {
                enemys[(int) (a.getX() / net)][(int) (a.getY() / net)] = 2;
                notSkip[0] = true;
            }
        });
        if (!notSkip[0]) {
            return false;
        }
        int minValue = 0;
        Point nuclearPoint = null;
        Vehicle nuclearAlly = null;
        Map<Point, Integer> groupCount = new HashMap<>();
        Map<Point, Integer> deadCount = new HashMap<>();
        for (Vehicle v : streamVehicles(Ownership.ENEMY).collect(Collectors.toList())) {
            if (enemys[(int) (v.getX() / net)][(int) (v.getY() / net)] != 2) {
                continue;
            }
            Point p = new Point(v.getX(), v.getY());
            int enemyCountAround = 1;
            int deadCountAround = 0;
            for (Vehicle v2 : streamVehicles(Ownership.ENEMY).collect(Collectors.toList())) {
                if (v2.getDistanceTo(p.x, p.y) < game.getTacticalNuclearStrikeRadius()) {
                    enemyCountAround++;
                }
                if (v2.getDistanceTo(p.x, p.y) < game.getTacticalNuclearStrikeRadius() && v2.getDurability() <= 99 * (1 - v2.getDistanceTo(p.x, p.y) / game.getTacticalNuclearStrikeRadius())) {
                    deadCountAround++;
                }
            }
            groupCount.put(p, enemyCountAround);
            deadCount.put(p, deadCountAround);
        }


        for (Vehicle ally : streamVehicles(Ownership.ALLY).collect(Collectors.toList())) {
            for (Vehicle enemy : streamVehicles(Ownership.ENEMY).collect(Collectors.toList())) {
                Point p = new Point(enemy.getX(), enemy.getY());
                Integer enemyCount = groupCount.get(p);
                Integer enemyDeadCount = deadCount.get(p);
                if (enemyCount == null) {
                    continue;
                }

                if (isVisible(ally, p)
                        && minValue < enemyCount + enemyDeadCount * 2
                        && enemyCount > 5
                        && distance(p, groundMass) > game.getTacticalNuclearStrikeRadius()
                        && distance(p, airMass) > game.getTacticalNuclearStrikeRadius()) {
                    nuclearPoint = p;
                    nuclearAlly = ally;
                    minValue = enemyCount + enemyDeadCount * 2;
                }
            }
        }
        if (nuclearPoint == null || nuclearAlly == null) {
            return false;
        }
        final Point finalNuclearPoint = nuclearPoint;
        final Vehicle finalNuclearAlly = nuclearAlly;
        if (finalNuclearAlly.getGroups().length > 0) {
            nuclearGroup = finalNuclearAlly.getGroups()[0];
            if ((nuclearGroup == AIR && startAir) || (nuclearGroup == GROUND && startGround)) {
                nuclearDeq.add(move ->
                {
                    move.setAction(ActionType.CLEAR_AND_SELECT);
                    move.setRight(world.getWidth());
                    move.setBottom(world.getHeight());
                    move.setGroup(finalNuclearAlly.getGroups()[0]);
                    selectedGroup = finalNuclearAlly.getGroups()[0];
                });
                nuclearDeq.add(move ->
                {
                    move.setAction(ActionType.MOVE);
                    move.setX(0);
                    move.setY(0);
                });
            }
        }
        nuclearDeq.add(move ->
        {
            if (isVisible(vehicleById.get(finalNuclearAlly.getId()), finalNuclearPoint)) {
                move.setAction(ActionType.TACTICAL_NUCLEAR_STRIKE);
                move.setX(finalNuclearPoint.x);
                move.setY(finalNuclearPoint.y);
                move.setVehicleId(finalNuclearAlly.getId());
            }
        });
        return true;
    }

    private boolean isVisible(Vehicle v, Point p) {
        double dist = v.getDistanceTo(p.x, p.y);
        double coeff = 1;
        int len = weatherTypeByCellXY.length;
        if (v.isAerial()) {
            if (weatherTypeByCellXY[(int) v.getX() / len][(int) v.getY() / len] == WeatherType.RAIN) {
                coeff *= game.getRainWeatherVisionFactor();
            }
            if (weatherTypeByCellXY[(int) v.getX() / len][(int) v.getY() / len] == WeatherType.CLOUD) {
                coeff *= game.getCloudWeatherVisionFactor();
            }
        } else {
            if (terrainTypeByCellXY[(int) v.getX() / len][(int) v.getY() / len] == TerrainType.FOREST) {
                coeff *= game.getForestTerrainVisionFactor();
            }
            if (terrainTypeByCellXY[(int) v.getX() / len][(int) v.getY() / len] == TerrainType.SWAMP) {
                coeff *= game.getSwampTerrainVisionFactor();
            }
        }

        return dist < coeff * v.getVisionRange() - 1;
    }

    private boolean inBattle(int group) {
        final double[] distance = {99999};
        streamVehicles(Ownership.ALLY).filter((v) -> v.getGroups()[0] == group).forEach((x) ->
                streamVehicles(Ownership.ENEMY).forEach((y) ->
                {
                    double dist = x.getDistanceTo(y);
                    if (distance[0] > dist) {
                        distance[0] = dist;
                    }
                }));
        return distance[0] < 11;
    }

    private boolean canHit(VehicleType t1, VehicleType t2) {
        switch (t1) {
            case FIGHTER:
                return t2 == FIGHTER || t2 == HELICOPTER;
            case TANK:
            case IFV:
            case HELICOPTER:
                return true;
            default:
                return false;
        }
    }

    private Point getGroupMass(int group) {
        double x = streamVehicles(Ownership.ALLY
        ).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group).mapToDouble(Vehicle::getX).average().orElse(0d);

        double y = streamVehicles(
                Ownership.ALLY
        ).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group).mapToDouble(Vehicle::getY).average().orElse(0d);
        return new Point((int) x, (int) y);
    }

    private Point getMassOfVehicle(Ownership ownership, VehicleType... vehicleTypes) {
        double x = streamVehicles(
                ownership, vehicleTypes
        ).mapToDouble(Vehicle::getX).average().orElse(0d);

        double y = streamVehicles(
                ownership, vehicleTypes
        ).mapToDouble(Vehicle::getY).average().orElse(0d);
        return new Point((int) x, (int) y);
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership, VehicleType... vehicleTypes) {
        Predicate<Vehicle> predicate = (vehicle) ->
        {
            if (ownership == Ownership.ALLY && vehicle.getPlayerId() != me.getId()) {
                return false;
            }
            if (ownership == Ownership.ENEMY && vehicle.getPlayerId() == me.getId()) {
                return false;
            }
            if (vehicleTypes != null && vehicleTypes.length > 0 && vehicleTypes[0] != null) {
                boolean o = false;
                for (VehicleType t : vehicleTypes) {
                    if (vehicle.getType() == t) {
                        o = true;
                    }
                }
                if (!o) {
                    return false;
                }
            }
            return true;
        };

        ArrayList<Vehicle> list = new ArrayList<>();
        for (Vehicle vehicle : vehicleById.values()) {
            if (predicate.test(vehicle)) {
                list.add(vehicle);
            }
        }

        return list.stream();
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership) {
        return streamVehicles(ownership, (VehicleType) null);
    }

    private Stream<Vehicle> streamVehicles() {
        return streamVehicles(Ownership.ANY);
    }

    private enum Ownership {
        ANY,

        ALLY,

        ENEMY
    }

    private class Point {
        public double x, y;

        Point(double xT, double yT) {
            x = xT;
            y = yT;
        }

        double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        @Override
        public String toString() {
            return "Point{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Point point = (Point) o;

            if (Double.compare(point.x, x) != 0) {
                return false;
            }
            return Double.compare(point.y, y) == 0;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(x);
            result = (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(y);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }
}