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

import static java.lang.Math.max;
import static java.lang.Math.min;
import static model.VehicleType.ARRV;
import static model.VehicleType.FIGHTER;
import static model.VehicleType.HELICOPTER;
import static model.VehicleType.IFV;
import static model.VehicleType.TANK;


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
    private int ping = 130;
    private Queue<Consumer<Move>> groupDeq = new ArrayDeque<>();
    private Queue<Consumer<Move>> nuclearDeq = new ArrayDeque<>();
    private boolean init = false;
    private boolean start = false;
    private int nuclearGroup = 0;
    private int selectedGroup = 0;
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

        if (fireInTheHole(world.getOpponentPlayer())) {
            nuclearEnemyAvoid();
        } else {
            getWith();
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

    private Group createGroup(double l, double r, double t, double b, VehicleType type) {
        select(groupDeq, l, r, t, b, type);
        assign(groupDeq, assignIndex);
        Point center = new Point((l + r) / 2, (t + b) / 2);
        Group group = new Group();
        group.index = assignIndex;
        group.maxSpeed = getSpeed(type);
        group.visionRange = getVisionRange(type);
        group.arial = type == FIGHTER || type == HELICOPTER;
        group.type = type;
        group.mass = center;
        group.target = center;
        group.from = center;
        groups.put(assignIndex, group);
        assignIndex++;
        scale(groupDeq, center, 0.1, group);
        return group;
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
        List<Integer> toRemove = new ArrayList<>();
        for (Group group : groups.values()) {
            group.mass = getGroupMass(group.index);
            group.vehicles = streamVehicles(Ownership.ALLY).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group.index).collect(Collectors.toList());
            group.radius = group.vehicles.stream().mapToDouble(v -> v.getDistanceTo(group.mass.x, group.mass.y)).max().orElse(0);
            group.facility = null;
            group.size = group.vehicles.size();
            group.enough = enough(group.index);
            if (group.size == 0) {
                toRemove.add(group.index);
                continue;
            }
            if (group.enough && group.scaling) {
                group.scaling = false;
            }
            final Point[] nearestEnemyFinal = {null};
            final Vehicle[] enemy = {null};
            final double[] distance = {99999, 99999};
            streamVehicles(
                    Ownership.ENEMY
            ).forEach((x) ->
            {
                if (distance[1] > distance(group.mass, new Point((int) x.getX(), (int) x.getY()))) {
                    distance[1] = distance(group.mass, new Point((int) x.getX(), (int) x.getY()));
                    nearestEnemyFinal[0] = new Point((int) x.getX(), (int) x.getY());
                    enemy[0] = x;
                }
            });
            group.nearestEnemy = nearestEnemyFinal[0];
            group.enemy = enemy[0];

        }
        for (int index : toRemove) {
            groups.remove(index);
        }

        facilities.sort((f1, f2) ->
        {
            if (f1.getType() == FacilityType.VEHICLE_FACTORY && f2.getType() == FacilityType.CONTROL_CENTER) {
                return -1;
            }
            if (f2.getType() == FacilityType.VEHICLE_FACTORY && f1.getType() == FacilityType.CONTROL_CENTER) {
                return 1;
            }

            double dist1 = 99999, dist2 = 99999;
            for (Group group : groups.values()) {
                if (group.type == FIGHTER || group.type == HELICOPTER) {
                    continue;
                }
                if (dist1 > distance(group.mass, new Point(f1.getLeft(), f1.getTop()))) {
                    dist1 = distance(group.mass, new Point(f1.getLeft(), f1.getTop()));
                }
                if (dist2 > distance(group.mass, new Point(f2.getLeft(), f2.getTop()))) {
                    dist2 = distance(group.mass, new Point(f2.getLeft(), f2.getTop()));
                }
            }
            return Double.compare(dist1, dist2);
        });

        for (Facility f : facilities) {
            if (f.getOwnerPlayerId() == me.getId()) {
                continue;
            }
            double dist = 99999;
            Group toAssign = null;
            Point facilityPoint = new Point(f.getLeft() + 32, f.getTop() + 32);
            for (Group group : groups.values()) {
                if (!group.arial && distance(facilityPoint, group.mass) < dist && group.facility == null) {
                    toAssign = group;
                    dist = distance(facilityPoint, group.mass);
                }
            }
            if (toAssign != null) {
                toAssign.facility = facilityPoint;
            }
        }
    }

    private void go() {
        if (groups.values().stream().anyMatch(g -> distance(g.mass, g.nearestEnemy) < g.visionRange + g.radius)) {
            nuclearAttack();
        }

        buildVehicles();
        assignNewVehicles();

        List<Group> priority = new ArrayList<>(groups.values());
        priority.sort((g1, g2) ->
        {
            if (!g1.enough && !g2.enough) {
                return 0;
            }
            if (!g1.enough) {
                return 1;
            }
            if (!g2.enough) {
                return -1;
            }
            return Double.compare(distance(g1.mass, g1.nearestEnemy), distance(g2.mass, g2.nearestEnemy));
        });

        List<Group> toRemove = groups.values().stream().filter(g -> g.scaling).collect(Collectors.toList());
        priority.removeAll(toRemove);

        for (Group g : priority) {
            if (g.enough && !(fireInTheHole(me) && nuclearGroup == g.index)) {
                if (!assignFacility(g)) {
                    Point attackPoint = attack(g);
                    if (!attackPoint.equals(g.mass)) {
                        if (selectedGroup != g.index) {
                            selectGroup(groupDeq, g.index);
                        }
                        moveFromTo(groupDeq, g.mass, attackPoint, g.maxSpeed, g.index);
                    }
                }
            }
        }
    }

    private boolean assignFacility(Group group) {
        if (group.facility == null) {
            return false;
        }

        Point save = savePoint(group.mass, group.facility, group, true);
        selectGroup(groupDeq, group.index);
        moveFromTo(groupDeq, group.mass, save, group.maxSpeed, group.index);
        return true;
    }

    private Point attack(Group group) {
        if (group.nearestEnemy == null) {
            return group.mass;
        }
        final Point nearestEnemy = group.nearestEnemy;

        if (group.arial && getGroupAvarageHealth(group.index) > 0.7) {
            Group arrvGroup = groups.values().stream().filter(g -> g.type == ARRV).findFirst().orElse(null);
            if (arrvGroup != null) {
                return savePoint(group.mass, arrvGroup.mass, group, true);
            }
        }

        if (shouldFight(group)) {
            return savePoint(group.mass, nearestEnemy, group, false);
        }

        Point t = null;
        if ((t = canFight(group)) != null) {
            return savePoint(group.nearestAlly, t, group, false);
        }

        double dist = distance(group.mass, nearestEnemy);
        double coeff;
        if (me.getRemainingNuclearStrikeCooldownTicks() > 100 && group.type != ARRV) {
            coeff = 1.2;
        } else {
            coeff = 0.6;
        }

        Point target = new Point(group.mass.getX() + (nearestEnemy.getX() - group.mass.getX()) / dist * (dist - game.getFighterVisionRange() * coeff - group.maxSpeed * game.getTacticalNuclearStrikeDelay()),
                group.mass.getY() + (nearestEnemy.getY() - group.mass.getY()) / dist * (dist - game.getFighterVisionRange() * coeff - group.maxSpeed * game.getTacticalNuclearStrikeDelay()));

        return savePoint(group.mass, target, group, false);
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

        if (delayedMove == null) {
            return false;
        }

        delayedMove.accept(move);
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

    private Point savePoint(Point from, Point to, Group group, boolean enemyAvoid) {
        double len = distance(from, to);
        Point vector;
        if (len > ping * group.maxSpeed) {
            vector = new Point((to.x - from.x) / len * (ping * group.maxSpeed), (to.y - from.y) / len * (ping * group.maxSpeed));
        } else {
            vector = new Point(to.x - from.x, to.y - from.y);
        }

        double ang = 0;
        while (ang < Math.PI) {
            Point tempVector = turnVector(vector, ang);
            if (isFree(from, new Point(from.x + tempVector.x, from.y + tempVector.y), group, enemyAvoid)) {
                return new Point(from.x + tempVector.x, from.y + tempVector.y);
            }
            tempVector = turnVector(vector, -ang);
            if (isFree(from, new Point(from.x + tempVector.x, from.y + tempVector.y), group, enemyAvoid)) {
                return new Point(from.x + tempVector.x, from.y + tempVector.y);
            }

            ang += Math.PI / 8;
        }

        return group.mass;
    }

    private Point turnVector(Point v, double ang) {
        return new Point((v.x * Math.cos(ang) - v.y * Math.sin(ang)), (v.x * Math.sin(ang) + v.y * Math.cos(ang)));
    }

    Line createLine(Point a, Point b) {
        Line s = new Line();
        s.a = a.y - b.y;
        s.b = b.x - a.x;
        s.c = a.x * b.y - b.x * a.y;
        return s;
    }

    double r(Point a, Point b) //расстояние между двумя точками
    {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
    }

    Point projection(Point a, Point b, Point c) // проекция точки С на прямую AB
    {
        double x = b.y - a.y; //x и y - координаты вектора, перпендикулярного к AB
        double y = a.x - b.x;
        double L = (a.x * b.y - b.x * a.y + a.y * c.x - b.y * c.x + b.x * c.y - a.x * c.y) / (x * (b.y - a.y) + y * (a.x - b.x));
        Point H = new Point(c.x + x * L, c.y + y * L);
        return H;
    }

    Point intersection(Line a, Line b) //точка пересечения прямых a и b
    {
        Point ans = new Point(0, 0);
        ans.x = (b.c * a.b - a.c * b.b) / (a.a * b.b - b.a * a.b);
        ans.y = (a.c * b.a - b.c * a.a) / (b.b * a.a - a.b * b.a);
        return ans;
    }

    double checkProjection(Point A, Point B, Point C) {
        Point H = projection(A, B, C);
        if ((H.x >= min(A.x, B.x) && H.x <= max(A.x, B.x)) && (H.y >= min(A.y, B.y) && H.y <= max(A.y, B.y))) //проекция принадлежит отрезку
        {
            return r(H, C);
        }
        return 999999;
    }

    double distanceIfNotIntersect(Point A, Point B, Point C, Point D) //расстояние между отрезками, если они не пересекаются
    {
        double min_dis = min(min(r(A, C), r(A, D)), min(r(B, C), r(B, D)));
        min_dis = min(min_dis, checkProjection(A, B, C));
        min_dis = min(min_dis, checkProjection(A, B, D));
        min_dis = min(min_dis, checkProjection(C, D, A));
        min_dis = min(min_dis, checkProjection(C, D, B));
        return min_dis;
    }

    private double vectorDistance(Point A, Point B, Point C, Point D) {
        Line AB = createLine(A, B);
        Line CD = createLine(C, D);
        if (AB.a / CD.a == AB.b / CD.b) //условие параллельности прямых
        {
            return distanceIfNotIntersect(A, B, C, D);
        } else {
            Point H = intersection(AB, CD);
            if ((H.x >= min(A.x, B.x) && H.x <= max(A.x, B.x) && H.x >= min(C.x, D.x) && H.x <= max(C.x, D.x)) && //точка пересечения принадлежит обоим отрезкам
                    (H.y >= min(A.y, B.y) && H.y <= max(A.y, B.y) && H.y >= min(C.y, D.y) && H.y <= max(C.y, D.y))) {
                return 0;
            } else {
                return distanceIfNotIntersect(A, B, C, D);
            }
        }
    }

    boolean intersectCircle(Point center, double radius, Point p1, Point p2) {
        return distance(center, p1) < radius || distance(center, p2) < radius || checkProjection(p1, p2, center) < radius;
    }

    private double getAngle(Point a, Point b) {
        int sign = a.x * b.y - b.x * a.y > 0 ? 1 : -1;
        return sign * Math.acos((a.x * b.x + a.y * b.y) / (Math.sqrt(a.x * a.x + a.y * a.y) * Math.sqrt(b.x * b.x + b.y * b.y)));
    }

    private boolean isFree(Point from, Point to, Group g, boolean enemyAvoid) {
        for (Group group : groups.values()) {
            if (g.index != group.index && group.arial == g.arial) {
                double radius = g.radius + group.radius + 6;

                if (distance(from, group.mass) < radius) {
                    Point vec1 = new Point(to.x - from.x, to.y - from.y);
                    Point vec2 = new Point(group.mass.x - from.x, group.mass.y - from.y);
                    if (Math.abs(getAngle(vec1, vec2)) > Math.PI / 2) {
                        continue;
                    } else {
                        return false;
                    }
                }

                if (g.mass.equals(g.target)) {
                    if (intersectCircle(group.mass, radius, from, to)) {
                        return false;
                    }
                } else {
                    if (vectorDistance(from, to, group.from, group.target) < radius) {
                        return false;
                    }
                }
            }
        }
        if (enemyAvoid && g.enemy != null) {
            if (distance(g.nearestEnemy, to) < max(g.enemy.getGroundAttackRange(), g.enemy.getGroundAttackRange()) + g.radius && distance(g.nearestEnemy, g.mass) > distance(g.nearestEnemy, to)) {
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
        groups.get(group).target = to;
        groups.get(group).from = from;
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

    private void scale(Queue<Consumer<Move>> deq, Point from, double factor, Group g) {
        deq.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(from.getX());
            move.setY(from.getY());
            move.setFactor(factor);
            g.scaling = true;
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

    private void select(Queue<Consumer<Move>> deq, double l, double r, double t, double b, VehicleType type) {
        deq.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(r);
            move.setBottom(b);
            move.setTop(t);
            move.setLeft(l);
            move.setVehicleType(type);
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
        if (p1 == null || p2 == null) {
            return 0;
        }
        return Math.sqrt((p1.getX() - p2.getX()) * (p1.getX() - p2.getX()) + (p1.getY() - p2.getY()) * (p1.getY() - p2.getY()));
    }

    private void selectGroup(Queue<Consumer<Move>> deq, int group) {
        deq.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setBottom(world.getHeight());
            move.setGroup(group);
            selectedGroup = group;
        });
    }

    private void getWith() {
        if (groups.values().stream().anyMatch(g -> g.scaled)) {
            nuclearDeq.clear();
            for (Group g : groups.values()) {
                if (g.scaled) {
                    selectGroup(nuclearDeq, g.index);
                    scale(nuclearDeq, g.mass, 0.1, g);
                    g.scaled = false;
                }
            }
        }
    }

    public boolean nuclearEnemyAvoid() {
        if (!fireInTheHole(world.getOpponentPlayer())) {
            return false;
        }

        Point enemyNuclearSrtike = new Point(world.getOpponentPlayer().getNextNuclearStrikeX(), world.getOpponentPlayer().getNextNuclearStrikeY());
        for (Group g : groups.values()) {
            if (g.vehicles == null || g.vehicles.size() == 0) {
                continue;
            }
            for (Vehicle v : g.vehicles) {
                if (v.getDistanceTo(enemyNuclearSrtike.x, enemyNuclearSrtike.y) < game.getTacticalNuclearStrikeRadius()) {
                    selectGroup(nuclearDeq, g.index);
                    scale(nuclearDeq, enemyNuclearSrtike, 10, g);
                    g.scaled = true;
                    break;
                }
            }
        }

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

    private void assignNewVehicles() {
        for (Facility f : facilities) {
            if (f.getOwnerPlayerId() == me.getId() && f.getType() == FacilityType.VEHICLE_FACTORY) {
                int newhelicopterCount = countVehiclesAruond(new Point(f.getLeft() + 32, f.getTop() + 32), f.getVehicleType());
                if (newhelicopterCount > 32) {
                    nuclearDeq.add(move ->
                    {
                        move.setAction(ActionType.SETUP_VEHICLE_PRODUCTION);
                        move.setFacilityId(f.getId());
                        move.setVehicleType(f.getVehicleType() == HELICOPTER ? IFV : HELICOPTER);
                    });
                    Group newGroup = createGroup(f.getLeft(), f.getLeft() + 64, f.getTop(), f.getTop() + 64, f.getVehicleType());
                }
            }
        }
    }

    private int countVehiclesAruond(Point p, VehicleType type) {
        return (int) streamVehicles(Ownership.ALLY, type).filter(h -> h.getDistanceTo(p.x, p.y) < 50 && h.getGroups().length == 0).count();
    }

    private void buildVehicles() {
        for (Facility f : facilities) {
            if (f.getOwnerPlayerId() == me.getId() && f.getVehicleType() != HELICOPTER && f.getVehicleType() != IFV && f.getType() == FacilityType.VEHICLE_FACTORY) {
                nuclearDeq.add(move ->
                {
                    move.setAction(ActionType.SETUP_VEHICLE_PRODUCTION);
                    move.setFacilityId(f.getId());
                    move.setVehicleType(HELICOPTER);
                });
            }
        }
    }

    private int getGroupDurability(int group) {
        return streamVehicles(Ownership.ALLY).filter(v -> v.getGroups().length > 0 && v.getGroups()[0] == group).mapToInt(Vehicle::getDurability).sum();
    }

    private Point canFight(Group g) {
        if (g.enemy == null) {
            return null;
        }

        if (isGoodAgainst(g.type, g.enemy.getType())) {
            Point result = null;
            double dist = 9999;
            for (Vehicle v : g.vehicles) {
                if (v.getDistanceTo(g.enemy) < dist) {
                    dist = v.getDistanceTo(g.enemy);
                    double len = dist - v.getAerialAttackRange();
                    result = new Point(v.getX() + (g.nearestEnemy.x - v.getX()) / dist * len, v.getY() + (g.nearestEnemy.y - v.getY()) / dist * len);
                    g.nearestAlly = new Point(v.getX(), v.getY());
                }
            }
            return result;
        }
        return null;
    }

    private boolean isGoodAgainst(VehicleType t1, VehicleType t2) {
        switch (t1) {
            case ARRV:
                return false;
            case FIGHTER:
                return t2 == HELICOPTER || t2 == FIGHTER;
            case HELICOPTER:
                return t2 == TANK || t2 == ARRV || t2 == IFV || t2 == HELICOPTER;
            case IFV:
                return t2 == HELICOPTER || t2 == FIGHTER || t2 == ARRV;
            case TANK:
                return t2 == FIGHTER || t2 == TANK || t2 == IFV || t2 == ARRV;
        }
        return false;
    }

    private boolean shouldFight(Group group) {
        Point p = group.nearestEnemy;
        List<Vehicle> enemys = streamVehicles(Ownership.ENEMY).collect(Collectors.toList());
        if (enemys.size() == 0 || p == null || !canHit(group.type, group.enemy.getType())) {
            return false;
        }
        enemys.sort(Comparator.comparingDouble(e -> e.getDistanceTo(p.x, p.y)));
        double sumDurability = 0;
        double distPrev = enemys.get(0).getDistanceTo(p.x, p.y);
        for (int i = 1; i < enemys.size(); i++) {
            Vehicle enemy = enemys.get(i);
            if (distPrev + 10 > enemy.getDistanceTo(p.x, p.y)) {
                distPrev = enemy.getDistanceTo(p.x, p.y);
                sumDurability += enemy.getDurability();

            } else {
                break;
            }
        }

        double allyDurability = group.vehicles.stream().mapToDouble(Vehicle::getDurability).sum();

        return sumDurability < allyDurability;
    }

    private double getPowerAgainst(Vehicle v, VehicleType t2) {
        if (t2 == HELICOPTER || t2 == FIGHTER) {
            return v.getAerialDamage();
        }
        return v.getGroundDamage();
    }

    private double getGroupAvarageHealth(int group) {
        double durability = 0;
        List<Vehicle> vList = streamVehicles(Ownership.ALLY).filter((v) -> v.getGroups().length > 0 && v.getGroups()[0] == group).collect(Collectors.toList());
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
            Group ng = groups.values().stream().filter(g -> finalNuclearAlly.getGroups()[0] == g.index).findFirst().orElse(null);
            if (ng != null) {
                if (!ng.scaling) {
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
                        nuclearGroup = finalNuclearAlly.getGroups()[0];
                    });
                } else {
                    return false;
                }
            }
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
        if (v == null) {
            return false;
        }
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
        Point target;
        Point from;
        List<Vehicle> vehicles;
        double maxSpeed;
        double radius;
        double visionRange;
        boolean arial;
        boolean enough;
        Point facility;
        long size;
        Point nearestEnemy;
        Point nearestAlly;
        Vehicle enemy;
        boolean scaled;
        boolean scaling;
    }

    private class Line {
        double a, b, c;
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

            Point Point = (Point) o;

            if (Double.compare(Point.x, x) != 0) {
                return false;
            }
            return Double.compare(Point.y, y) == 0;
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