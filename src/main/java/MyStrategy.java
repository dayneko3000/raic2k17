import model.ActionType;
import model.Facility;
import model.FacilityType;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static model.VehicleType.ARRV;
import static model.VehicleType.FIGHTER;
import static model.VehicleType.HELICOPTER;
import static model.VehicleType.IFV;


@SuppressWarnings({"UnsecureRandomNumberGeneration", "FieldCanBeLocal", "unused", "OverlyLongMethod"})
public final class MyStrategy implements Strategy {
    private final Map<Long, Vehicle> vehicleById = new HashMap<>();
    private final Map<Long, Integer> updateTickByVehicleId = new HashMap<>();
    int assignIndex = 1;
    private Random random;
    private TerrainType[][] terrainTypeByCellXY;
    private WeatherType[][] weatherTypeByCellXY;
    private List<Facility> facilities;
    private Player me;
    private World world;
    private Game game;
    private Move move;
    private int ping = 80;
    private Queue<Consumer<Move>> groupDeq = new ArrayDeque<>();
    private Queue<Consumer<Move>> nuclearDeq = new ArrayDeque<>();
    private boolean init = false;
    private boolean start = false;
    private Map<Integer, Group> groups = new HashMap<>();


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

        ready();
        go();
        executeDelayedMove();
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

        if (!init) {
            setGroups();
            init = true;
        }

    }

    private void setGroups() {
        for (VehicleType type : VehicleType.values()) {
            Point l = getMinVehicle(type);
            Point r = getMaxVehicle(type);
            if (type != FIGHTER && type != ARRV) {

                createGroup(l.x, l.x + (r.x - l.x) / 2, l.y, l.y + (r.y - l.y) / 2, type);
                assignIndex++;
                createGroup(l.x + (r.x - l.x) / 2, r.x, l.y, l.y + (r.y - l.y) / 2, type);
                assignIndex++;
                createGroup(l.x, l.x + (r.x - l.x) / 2, l.y + (r.y - l.y) / 2, r.y, type);
                assignIndex++;
                createGroup(l.x + (r.x - l.x) / 2, r.x, l.y + (r.y - l.y) / 2, r.y, type);
                assignIndex++;
            } else {
                createGroup(l.x, r.x, l.y, r.y, type);
            }
        }
    }

    private void createGroup(double l, double r, double t, double b, VehicleType type) {
        select(groupDeq, l, r, t, b);
        assign(groupDeq, assignIndex);
        Point center = new Point((l + r) / 2, (t + b) / 2);
        scale(groupDeq, center, 0.1);
        Group group = new Group();
        group.index = assignIndex;
        group.maxSpeed = getSpeed(type);
        group.visionRange = getVisionRange(type);
        group.arial = type == FIGHTER || type == HELICOPTER;
        group.type = type;
        group.mass = center;
        groups.put(assignIndex, group);
        assignIndex++;
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

    private double getVisionRange(VehicleType type) {
        switch (type) {
            case ARRV:
                return game.getArrvVisionRange();
            case FIGHTER:
                return game.getFighterVisionRange();
            case HELICOPTER:
                return game.getHelicopterVisionRange();
            case IFV:
                return game.getIfvVisionRange();
            default:
                return game.getTankVisionRange();
        }
    }

    private void ready() {
        for (Group group : groups.values()) {
            group.mass = getGroupMass(group.index);
            group.radius = streamVehicles(Ownership.ALLY).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group.index).mapToDouble(v -> v.getDistanceTo(group.mass.x, group.mass.y)).max().orElse(0);
            group.facilityId = -1;
        }
    }

    private void go() {
        nuclearAttack();
        for (Group group : groups.values()) {
            selectGroup(groupDeq, group.index);
            if (group.arial || !assignFacility(group)) {
                attack(group);
            }
        }
    }

    private boolean assignFacility(Group group) {
        Point facility = getFacility(group);

        for (Group group1 : groups.values()) {
            if (!group1.arial && distance(group1.mass, facility) < distance(group.mass, facility)) {
                return false;
            }
        }

        Point save = savePoint(group.mass, facility, group);
        moveFromTo(groupDeq, group.mass, save, group.maxSpeed * 0.6, group.index);
        return true;
    }

    private void attack(Group group) {
        final Point[] nearestEnemyFinal = {null};
        final double[] distance = {99999, 99999};
        streamVehicles(
                Ownership.ENEMY
        ).forEach((x) ->
        {
            if (distance[1] > distance(group.mass, new Point((int) x.getX(), (int) x.getY()))) {
                distance[1] = distance(group.mass, new Point((int) x.getX(), (int) x.getY()));
                nearestEnemyFinal[0] = new Point((int) x.getX(), (int) x.getY());
            }
        });
        Point nearestEnemy = nearestEnemyFinal[0];

        if (getSumEnemyDurabilityAround(nearestEnemy) < getGroupDurability(group.index)) {
            Point save = savePoint(group.mass, nearestEnemy, group);
            moveFromTo(groupDeq, group.mass, save, group.maxSpeed * 0.6, group.index);
        } else {
            double dist = distance(group.mass, nearestEnemy);
            double coeff;
            if (me.getRemainingNuclearStrikeCooldownTicks() > 100) {
                coeff = 1.2;
            } else {
                coeff = 0.6;
            }
            Point target = new Point(group.mass.getX() + (nearestEnemy.getX() - group.mass.getX()) / dist * (dist - game.getFighterVisionRange() * coeff - group.maxSpeed * game.getTacticalNuclearStrikeDelay()),
                    group.mass.getY() + (nearestEnemy.getY() - group.mass.getY()) / dist * (dist - game.getFighterVisionRange() * coeff - group.maxSpeed * game.getTacticalNuclearStrikeDelay()));

            Point save = savePoint(group.mass, target, group);

            moveFromTo(groupDeq, group.mass, save, group.maxSpeed * 0.6, group.index);
        }
    }

    Consumer<Move> getMove() {
        Consumer<Move> delayedMove = nuclearDeq.poll();
        if (delayedMove != null) {
            return delayedMove;
        }

        return groupDeq.poll();
    }

    /**
     * Достаём отложенное действие из очереди и выполняем его.
     *
     * @return Возвращает {@code true}, если и только если отложенное действие было найдено и выполнено.
     */
    private boolean executeDelayedMove() {
        Consumer<Move> delayedMove = getMove();

        if (delayedMove == null && groupDeq.size() == 0) {
            return false;
        }

        if (delayedMove != null) {
            delayedMove.accept(move);
        }
        return true;
    }

    private boolean fireInTheHole(Player p) {
        long centerCount = facilities.stream().filter(f -> f.getOwnerPlayerId() == p.getId() && f.getType() == FacilityType.CONTROL_CENTER).count();
        return p.getRemainingNuclearStrikeCooldownTicks() >= game.getBaseTacticalNuclearStrikeCooldown() - centerCount * game.getTacticalNuclearStrikeCooldownDecreasePerControlCenter() - game.getTacticalNuclearStrikeDelay();
    }

    private boolean enough(int group) {
        return streamVehicles(Ownership.ALLY).allMatch(
                vehicle -> world.getTickIndex() - updateTickByVehicleId.get(vehicle.getId()) > 5 || vehicle.getGroups().length == 0 || vehicle.getGroups()[0] != group
        );
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

    private Point getMaxVehicle(VehicleType type) {
        final double[] x = {0};
        final double[] y = {0};
        streamVehicles(Ownership.ALLY, type).forEach((v) ->
        {
            if (v.getX() >= x[0]) {
                x[0] = v.getX();
            }
            if (v.getY() >= y[0]) {
                y[0] = v.getY();
            }
        });
        return new Point(x[0], y[0]);
    }

    private void assign(Queue<Consumer<Move>> deq, int group) {
        deq.add((move) ->
        {
            move.setAction(ActionType.ASSIGN);
            move.setGroup(group);
        });
    }

    private void deselectGroup(Queue<Consumer<Move>> deq, int group) {
        deq.add((move) ->
        {
            move.setAction(ActionType.DESELECT);
            move.setGroup(group);
        });
    }

    private Point savePoint(Point from, Point to, Group group) {
        double len = distance(from, to);
        Point vector = new Point((to.x - from.x) / len * (ping * group.maxSpeed * 0.6), (to.y - from.y) / len * (ping * group.maxSpeed * 0.6));

        double ang = 0;
        while (ang < Math.PI) {
            Point tempVector = turnVector(vector, ang);
            if (isFree(new Point(from.x + tempVector.x, from.y + tempVector.y), group)) {
                return new Point(from.x + tempVector.x, from.y + tempVector.y);
            }
            tempVector = turnVector(vector, -ang);
            if (isFree(new Point(from.x + tempVector.x, from.y + tempVector.y), group)) {
                return new Point(from.x + tempVector.x, from.y + tempVector.y);
            }

            ang += Math.PI / 6;
        }


        return group.mass;
    }

    private Point turnVector(Point v, double ang) {
        return new Point((v.x * Math.cos(ang) - v.y * Math.sin(ang)), (v.x * Math.sin(ang) + v.y * Math.cos(ang)));
    }

    private boolean isFree(Point p, Group g) {
        for (Group group : groups.values()) {
            if (g.index != group.index && group.arial == g.arial && distance(group.mass, p) < g.radius + group.radius + 10) {
                return false;
            }
        }
        return true;
    }

    private void moveFromTo(Queue<Consumer<Move>> deq, Point from, Point to, Double maxSpeed, int group) {
        double minX = streamVehicles(Ownership.ALLY).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group).mapToDouble(Vehicle::getX).min().orElse(world.getWidth() / 2);
        double minY = streamVehicles(Ownership.ALLY).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group).mapToDouble(Vehicle::getY).min().orElse(world.getWidth() / 2);
        double maxX = streamVehicles(Ownership.ALLY).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group).mapToDouble(Vehicle::getX).max().orElse(world.getWidth() / 2);
        double maxY = streamVehicles(Ownership.ALLY).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group).mapToDouble(Vehicle::getY).max().orElse(world.getWidth() / 2);
        deq.add(move ->
        {
            move.setAction(ActionType.MOVE);
            double diffX = to.getX() - from.getX();
            if ((minX > 10 && diffX < 0) || (maxX < world.getWidth() - 10 && diffX > 0)) {
                move.setX(diffX);
            }
            double diffY = to.getY() - from.getY();
            if ((minY > 10 && diffY < 0) || (maxY < world.getHeight() - 10 && diffY > 0)) {
                move.setY(diffY);
            }
            move.setMaxSpeed(maxSpeed);
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

    private void nuclearEnemyAvoid(Queue<Consumer<Move>> deq, Point p, int group) {
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(10);
        });
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(0.1);
        });
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(1.1);
        });
    }

    private void rotateAround(Queue<Consumer<Move>> deq, Point p, double angle) {
        deq.add(move ->
        {
            move.setAction(ActionType.ROTATE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setAngle(angle);
            move.setMaxAngularSpeed(Math.PI / 500);
        });
    }

    private double getDistance(Point p1, Point p2) {
        return Math.sqrt((p1.getX() - p2.getX()) * (p1.getX() - p2.getX()) + (p1.getY() - p2.getY()) * (p1.getY() - p2.getY()));
    }

    private void select(Queue<Consumer<Move>> deq, double l, double r, double t, double b) {
        deq.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(r);
            move.setBottom(b);
            move.setTop(t);
            move.setLeft(l);
        });
    }

    private void selectVehicleType(Queue<Consumer<Move>> deq, VehicleType vehicleType) {
        deq.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setBottom(world.getHeight());
            if (vehicleType != null) {
                move.setVehicleType(vehicleType);
            }
        });
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

    private void selectGroup(Queue<Consumer<Move>> deq, int group) {
        deq.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setBottom(world.getHeight());
            move.setGroup(group);
        });
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
        //DOSHTH

        return true;
    }

    private Point getFacility(Group group) {
        if (facilities == null) {
            return null;
        }
        Facility nearest = null;
        double dist = 99999;
        for (Facility f : facilities) {

            if (f.getOwnerPlayerId() != me.getId() && (distance(new Point(f.getLeft(), f.getTop()), group.mass) < dist)) {
                nearest = f;
                dist = distance(new Point(f.getLeft() + 20, f.getTop() + 20), group.mass);
            }
        }
        if (nearest == null) {
            return null;
        }
        return new Point(nearest.getLeft() + 20, nearest.getTop() + 20);
    }

    private void nuclearReady() {
    }

    private void buildVehicles() {
        for (Facility f : facilities) {
            if (f.getOwnerPlayerId() == me.getId() && f.getVehicleType() != IFV) {
                nuclearDeq.add(move ->
                {
                    move.setAction(ActionType.SETUP_VEHICLE_PRODUCTION);
                    move.setFacilityId(f.getId());
                    move.setVehicleType(IFV);
                });
            }
        }
    }

    private int getGroupDurability(int group) {
        return streamVehicles(Ownership.ALLY).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group).mapToInt(Vehicle::getDurability).sum();
    }

    private int getSumEnemyDurabilityAround(Point p) {
        List<Vehicle> enemys = streamVehicles(Ownership.ENEMY).collect(Collectors.toList());
        if (enemys.size() == 0) {
            return 0;
        }
        enemys.sort(Comparator.comparingDouble(e -> e.getDistanceTo(p.x, p.y)));
        int result = enemys.get(0).getDurability();
        double distPrev = enemys.get(0).getDistanceTo(p.x, p.y);
        for (int i = 1; i < enemys.size(); i++) {
            Vehicle enemy = enemys.get(i);
            if (distPrev + 10 > enemy.getDistanceTo(p.x, p.y)) {
                distPrev = enemy.getDistanceTo(p.x, p.y);
                result += enemy.getDurability();
            } else {
                break;
            }
        }

        return result;
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

        int minValue = 0;
        Point nuclearPoint = null;
        Vehicle nuclearAlly = null;
        Map<Point, Integer> groupCount = new HashMap<>();
        Map<Point, Integer> deadCount = new HashMap<>();
        for (Vehicle v : streamVehicles(Ownership.ENEMY).collect(Collectors.toList())) {
            Point p = new Point(v.getX(), v.getY());
            //OPTIMIZE

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
                        && enemyCount > 5) {
                    nuclearPoint = p;
                    nuclearAlly = ally;
                    minValue = enemyCount + enemyDeadCount * 2;
                }
            }
        }
        if (nuclearPoint == null) {
            return false;
        }
        final Point finalNuclearPoint = nuclearPoint;
        final Vehicle finalNuclearAlly = nuclearAlly;
        if (finalNuclearAlly.getGroups().length > 0) {
            nuclearDeq.add(move ->
            {
                move.setAction(ActionType.CLEAR_AND_SELECT);
                move.setRight(world.getWidth());
                move.setBottom(world.getHeight());
                move.setGroup(finalNuclearAlly.getGroups()[0]);
            });
            nuclearDeq.add(move ->
            {
                move.setAction(ActionType.MOVE);
                move.setX(0);
                move.setY(0);
                System.out.println("stopped");
            });
        }
        nuclearDeq.add(move ->
        {
            if (isVisible(vehicleById.get(finalNuclearAlly.getId()), finalNuclearPoint)) {
                move.setAction(ActionType.TACTICAL_NUCLEAR_STRIKE);
                move.setX(finalNuclearPoint.x);
                move.setY(finalNuclearPoint.y);
                System.out.println("boom");
                move.setVehicleId(finalNuclearAlly.getId());
            } else {
                System.out.println("not visioned");
            }
        });
        return true;
    }

    private boolean isVisible(Vehicle v, Point p) {
        if (v == null)
            return false;
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

    private class Group {
        int index;
        VehicleType type;
        Point mass;
        double maxSpeed;
        double radius;
        double visionRange;
        boolean arial;
        int facilityId;
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