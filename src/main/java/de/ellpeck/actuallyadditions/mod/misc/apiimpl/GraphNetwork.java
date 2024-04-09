package de.ellpeck.actuallyadditions.mod.misc.apiimpl;

import de.ellpeck.actuallyadditions.api.laser.IConnectionPair;
import de.ellpeck.actuallyadditions.api.laser.INetwork;
import de.ellpeck.actuallyadditions.api.laser.LaserType;
import io.netty.util.internal.ConcurrentSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GraphNetwork implements INetwork {
	private AtomicInteger changeAmount = new AtomicInteger(0);
	
	/**
	 * Quick reverse lookup to get a BlockPos' representation in the graph.
	 * ?Could maybe use an {IntMap : Int2ObjectMap} for x and y coords to save memory? probably doesn't matter but whatever
	 */
	public Map<BlockPos, Node> nodeLookupMap;
	
	
	@Override
	public void addConnection(IConnectionPair pair) {
		BlockPos[] positions = pair.getPositions();
		
		Function<BlockPos, Node> mapper = pos -> new Node(pos, pair.getType());
		
		Node first = nodeLookupMap.computeIfAbsent(positions[0], mapper);
		Node second = nodeLookupMap.computeIfAbsent(positions[1], mapper);
		
		if (Objects.equals(first, second))
			return;
		
		
	}
	
	@Override
	public boolean removeConnection(IConnectionPair pair) {
		return false;
	}
	
	@Override
	public Set<IConnectionPair> getAllConnections() {
		return null;
	}
	
	@Override
	public Set<IConnectionPair> getConnectionsFor(BlockPos pos) {
		return null;
	}
	
	@Override
	public int getChangeAmount() {
		return changeAmount.get();
	}
	
	@Override
	public void incrementChangeAmount() {
		changeAmount.incrementAndGet();
	}
	
	public static int debug$idCount = 0;
	public final int id;
	
	public GraphNetwork() {
		nodeLookupMap = new ConcurrentHashMap<>();
		this.id = debug$idCount++;
	}
	
	public int getNodeCount() {
		return nodeLookupMap.size();
	}
	
	public Node removeNode(BlockPos pos) {
		return nodeLookupMap.remove(pos);
	}
	
	public Node getNodeFor(BlockPos pos) {
		return nodeLookupMap.get(pos);
	}
	
	/**
	 * Adds all of this network's endpoints and nodes to the other network and clears all of this network's trackers.
	 * @param otherNetwork The network to join.
	 */
	public void mergeIntoOtherNetwork(GraphNetwork otherNetwork) {
		// update every node's internal network reference
		forEach(node -> node.network = otherNetwork);
		otherNetwork.nodeLookupMap.putAll(this.nodeLookupMap);
		this.nodeLookupMap.clear();
	}
	
	/**
	 * Performs an action on every node in the graph. Used in {@link #mergeIntoOtherNetwork} to set every node's network reference to the new network.
	 * @param action The action to
	 */
	public void forEach(Consumer<Node> action) {
		if (action == null || nodeLookupMap.isEmpty()) {
			return;
		}
		
		
	}
	
	public void traverseFromNode(Node currentNode, Consumer<Node> action) {
		Set<Node> alreadyTraversed = new HashSet<>(this.nodeLookupMap.size());
		
		forEachNodeRecursive(currentNode, action, alreadyTraversed);
	}
	
	/**
	 * Put logic in another method because I don't want the recursive logic inside my forEach method.
	 * ...I could probably just put it inside a class to avoid pushing everything onto the stack...
	 * @param currentNode The current node being checked.
	 * @param action The action to perform on every node.
	 * @param alreadyChecked Set of nodes we've already looked at. Pass in a {@code new HashSet<>()} or something sized to the expected number of nodes you'll encounter.
	 */
	public static void forEachNodeRecursive(Node currentNode, Consumer<Node> action, Set<Node> alreadyChecked) {
		action.accept(currentNode);
		alreadyChecked.add(currentNode);
		
		for (Node connectedNode : currentNode.connections) {
			if (!alreadyChecked.contains(connectedNode)) {
				forEachNodeRecursive(connectedNode, action, alreadyChecked);
			}
		}
	}
	
	public static void forEachEdgeRecursive(Node currentNode, BiConsumer<Node, Node> action, Set<Node> alreadyChecked) {
		alreadyChecked.add(currentNode);
		for (Node connectedNode : currentNode.connections) {
			if (!alreadyChecked.contains(connectedNode)) {
				action.accept(currentNode, connectedNode);
				forEachEdgeRecursive(connectedNode, action, alreadyChecked);
			}
		}
	}
	
	@Override
	public NBTTagCompound serializeNBT() {
		NBTTagList list = new NBTTagList();
		
		forEachEdgeRecursive(this.nodeLookupMap.values().iterator().next(), (source, dest) -> {
			NBTTagCompound tag = new NBTTagCompound();
			source.makeConnectionPairWith(dest).writeToNBT(tag);
			list.appendTag(tag);
		}, new HashSet<>(this.nodeLookupMap.size()));
		
		NBTTagCompound compound = new NBTTagCompound();
		compound.setTag("Network", list);
		return compound;
	}
	
	@Override
	public void deserializeNBT(NBTTagCompound tag) {
		NBTTagList list = tag.getTagList("Network", 10);
		for (int i = 0; i < list.tagCount(); i++) {
			ConnectionPair pair = new ConnectionPair();
			pair.readFromNBT(list.getCompoundTagAt(i));
			BlockPos[] positions = pair.getPositions();
			
			// no reason to generate two classes for the same thing, shrug
			Function<BlockPos, Node> nodeGenerator = (k) -> new Node(k, pair.getType());
			Node first = this.nodeLookupMap.computeIfAbsent(positions[0], nodeGenerator);
			Node second = this.nodeLookupMap.computeIfAbsent(positions[1], nodeGenerator);
			
			connectNodes(first, second);
		}
	}
	
	/**
	 * Makes a two-way connection between two nodes.
	 */
	public static void connectNodes(Node first, Node second) {
		if (Objects.equals(first, second))
			return;
		first.connections.add(second);
		second.connections.add(first);
	}
	
	public static void disconnectNodes(Node first, Node second) {
		first.connections.remove(second);
		second.connections.remove(first);
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		if (!super.equals(o))
			return false;
		GraphNetwork that = (GraphNetwork) o;
		
		return id == that.id && Objects.equals(nodeLookupMap, that.nodeLookupMap);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(nodeLookupMap, id);
	}
	
	@Override
	public String toString() {
		String ret = "GraphNetwork[" + id + "]{";
		ret += nodeLookupMap.values()
		                    .stream()
		                    .map(node -> {
			                    String s = String.format("[%d %d %d]", node.pos.getX(), node.pos.getY(), node.pos.getZ());
			                    s += "::{";
			                    s += node.connections
					                    .stream()
					                    .map(connected -> {
						                    return String.format("(%d %d %d)", connected.pos.getX(), connected.pos.getY(), connected.pos.getZ());
					                    })
					                    .collect(Collectors.joining(", "));
			                    s += "}";
			                    return s;
		                    })
		                    .collect(Collectors.joining(", "));
		ret += "}";
		return ret;
	}
	
	
	/**
	 * Node that represents a relay in the Network.
	 */
	public static final class Node {
		/**
		 * Reference to the network that owns this node
		 */
		public GraphNetwork network = null;
		/**
		 * All nodes that are directly linked to this node. For use in rendering lasers and traversing the graph.
		 */
		public final Set<Node> connections = new HashSet<>();
		
		/**
		 * The value being encapsulated.
		 */
		public final BlockPos pos;
		
		/**
		 * The type of this relay.
		 */
		public final LaserType type;
		
		
		public Node(BlockPos pos, LaserType type) {
			this.pos = pos;
			this.type = type;
		}
		
		
		
		@Nonnull
		public ConnectionPair makeConnectionPairWith(@Nonnull Node other) {
			if (this.equals(other)) {
				//TODO warn with logger
			}
			return new ConnectionPair(
					this.pos,
					other.pos,
					this.type,
					false // always false in code fsr, instead just put as infrared if you're wearing goggles
			);
		}
		
		public ConcurrentSet<IConnectionPair> getConnectionsAsPairs() {
			ConcurrentSet<IConnectionPair> ret = new ConcurrentSet<>();
			this.connections.forEach(node -> ret.add(this.makeConnectionPairWith(node)));
			return ret;
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			Node node = (Node) o;
			return Objects.equals(network, node.network)
			       && Objects.equals(connections, node.connections)
			       && Objects.equals(pos, node.pos)
			       && type == node.type;
		}
		
		
		@Override
		public String toString() {
			String s = "RelayNode{[" + pos.getX() + ' ' + pos.getY() + ' ' + pos.getZ() + "]::(";
			if (!connections.isEmpty()) {
				s += connections.stream()
				                .map(el -> "[" + el.pos.getX() + ' ' + el.pos.getY() + ' ' + el.pos.getZ() + "]")
				                .collect(Collectors.joining(","));
			}
			return s + ")}";
		}
	}
}
