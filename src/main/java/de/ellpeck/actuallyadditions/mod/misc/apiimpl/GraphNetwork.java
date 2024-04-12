package de.ellpeck.actuallyadditions.mod.misc.apiimpl;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import de.ellpeck.actuallyadditions.api.laser.IConnectionPair;
import de.ellpeck.actuallyadditions.api.laser.INetwork;
import de.ellpeck.actuallyadditions.api.laser.LaserType;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.INBTSerializable;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GraphNetwork implements INetwork {
	
	public Map<BlockPos, Node> nodeLookupMap;
	
	private AtomicInteger changeAmount = new AtomicInteger(0);
	
	public static int debug$idCount = 0;
	public final int id;
	
	public GraphNetwork() {
		this.nodeLookupMap = Collections.synchronizedMap(new Object2ObjectOpenHashMap<>());
		this.id = debug$idCount++;
	}
	
	
	/**
	 * Gets nodes representing the positions in the connection pair, or create them if they are absent.  Uses a Set instead of a Tuple since the API technically allows for 3+ nodes to exist in a pair.
	 * @param pair The connection pair containing the positions to get nodes for.
	 * @return Set containing the distinct nodes in the pair.
	 */
	@Nonnull
	public Set<Node> getOrMakeNodesForConnectionPair(IConnectionPair pair) {
		Builder<Node> ret = ImmutableSet.builder();
		
		BlockPos[] positions = pair.getPositions();
		
		for (int i = 0; i < positions.length; i++) {
			ret.add(nodeLookupMap.computeIfAbsent(positions[i], pos -> new Node(pos, pair.getType())));
		}
		
		return ret.build();
	}
	
	
	@Override
	public void addConnection(IConnectionPair pair) {
		Set<Node> nodes = getOrMakeNodesForConnectionPair(pair);
		
		if (nodes.size() <= 1) // if all match
			return;
		
		connectNodes(nodes);
		incrementChangeAmount();
	}
	
	@Nullable
	@Override
	public Pair<Set<INetwork>, Set<BlockPos>> removeConnection(IConnectionPair pair, World world) {
		BlockPos[] positions = pair.getPositions();
		if (positions.length == 2) {
			return removeConnection(positions[0], positions[1], world);
		} else {
			Pair<Set<INetwork>, Set<BlockPos>> returnValue = Pair.of(new ObjectOpenHashSet<>(), new ObjectOpenHashSet<>());
			for (int i = 0; i < positions.length; i++) {
				for (int j = i + 1; j < positions.length; j++) {
					Pair<Set<INetwork>, Set<BlockPos>> fromRemove = removeConnection(positions[i], positions[j], world);
					if (fromRemove == null)
						continue;
					
					returnValue.getLeft().addAll(fromRemove.getLeft());
					returnValue.getRight().addAll(fromRemove.getRight());
				}
			}
			return returnValue;
		}
	}
	
	@Override
	public Pair<Set<INetwork>, Set<BlockPos>> removeConnection(BlockPos first, BlockPos second, World world) {
		
		Pair<Set<INetwork>, Set<BlockPos>> holder = Pair.of(new ObjectOpenHashSet<>(1), new ObjectOpenHashSet<>(2));
		removeConnection(getNodeFor(first), getNodeFor(second), holder); // TODO make this dynamic
		
		return holder;
	}
	
	private void removeConnection(Node n1, Node n2, Pair<Set<INetwork>, Set<BlockPos>> newNetworks_IsolatedNodes) {
		if (n1 == null || n2 == null || n1.equals(n2))
			return;
		
		// delete the nodes' references to one another
		Node.deleteTwoWayConnection(n1, n2);
		
		boolean firstWasEmpty = false;
		boolean secondWasEmpty = false;
		if (n1.connections.isEmpty()) {
			// we've isolated this node: need to remove its network reference
			nodeLookupMap.remove(n1.pos);
			newNetworks_IsolatedNodes.getRight().add(n1.pos);
			firstWasEmpty = true;
		}
		if (n2.connections.isEmpty()) {
			nodeLookupMap.remove(n2.pos);
			newNetworks_IsolatedNodes.getRight().add(n2.pos);
			secondWasEmpty = true;
		}
		// if one was isolated, that means we can leave anything on the other branch in this network and don't have to create another one
		if (firstWasEmpty || secondWasEmpty)
			return;
		
		GraphNetwork newNetwork = new GraphNetwork();
		traverseFromNode(n2, node -> newNetwork.nodeLookupMap.put(node.pos, node));
		newNetworks_IsolatedNodes.getLeft().add(newNetwork);
	}
	
	@Override
	public Pair<Set<INetwork>, Set<BlockPos>> removeRelay(BlockPos pos, World world) {
		Node removed = nodeLookupMap.remove(pos);
		if (removed == null)
			return null;
		
		final Pair<Set<INetwork>, Set<BlockPos>> newNetworks_isolatedNodes = Pair.of(new ObjectOpenHashSet<>(), new ObjectOpenHashSet<>());
		removed.connections.forEach(connectedNode -> removeConnection(removed, connectedNode, newNetworks_isolatedNodes));
		
		return newNetworks_isolatedNodes;
	}
	
	@Override
	public Set<IConnectionPair> getAllConnections() {
		Optional<Node> arbitraryNode = nodeLookupMap.values().stream().findFirst();
		if (!arbitraryNode.isPresent())
			return ImmutableSet.of();
		
		Set<IConnectionPair> ret = new ObjectOpenHashSet<>(nodeLookupMap.size());
		
		traverseFromNode(arbitraryNode.get(), (n1, n2) -> ret.add(n1.makeConnectionPairWith(n2)));
		
		return ret;
	}
	
	@Override
	public Set<IConnectionPair> getConnectionsFor(BlockPos pos) {
		Node node = nodeLookupMap.get(pos);
		if (node == null)
			return null;
		
		return node.getConnectionsAsPairs();
	}
	
	@Override
	public void absorbNetwork(INetwork other) {
		if (other instanceof GraphNetwork) {
			((GraphNetwork) other).forEach(node -> node.network = this);
			this.nodeLookupMap.putAll(((GraphNetwork) other).nodeLookupMap);
		} else {
			other.getAllConnections().forEach(this::addConnection);
		}
	}
	
	@Override
	public int getChangeAmount() {
		return changeAmount.get();
	}
	
	@Override
	public void incrementChangeAmount() {
		changeAmount.incrementAndGet();
	}
	
	/**
	 * Get the number of relays in this network.
	 */
	public int size() {
		return nodeLookupMap.size();
	}
	
	public boolean containsRelay(BlockPos pos) {
		return nodeLookupMap.containsKey(pos);
	}
	
	@Nullable
	public Node getNodeFor(BlockPos pos) {
		if (pos == null)
			return null;
		return nodeLookupMap.get(pos);
	}
	
	@Override
	public Set<BlockPos> getMembers() {
		return nodeLookupMap.keySet();
	}
	
	/**
	 * Performs an action on every node in the graph. Used in {@link #absorbNetwork(de.ellpeck.actuallyadditions.api.laser.INetwork)} to set every node's network reference to the new network.
	 * @param action The action to perform.
	 */
	public void forEach(Consumer<Node> action) {
		if (action == null || nodeLookupMap.isEmpty()) {
			return;
		}
		
		nodeLookupMap.values().forEach(action);
	}
	
	/**
	 * Performs the given action on every node on the same branch as the given node.  A pre-order traversal of the graph.
	 * @param start The node to start at.
	 * @param action The action to perform on every node.
	 */
	public final void traverseFromNode(Node start, Consumer<Node> action) {
		traverseFromNode(start, action, size());
	}
	
	/**
	 * Performs the given action on every node on the same branch as the given node.  A pre-order traversal of the graph.
	 * @param start The node to start at.
	 * @param action The action to perform on every node.
	 */
	public static void traverseFromNode(Node start, Consumer<Node> action, int expectedSize) {
		Set<Node> alreadyTraversed = new ObjectOpenHashSet<>(expectedSize);
		
		forEachNodeRecursive(start, action, alreadyTraversed);
	}
	
	/**
	 * Performs the given action on every edge on the same branch as the given node.  A pre-order traversal of the graph.
	 * @param start The node to start at.
	 * @param action The action to perform on every connection between nodes.
	 */
	public final void traverseFromNode(Node start, BiConsumer<Node, Node> action) {
		traverseFromNode(start, action, size());
	}
	
	/**
	 * Performs the given action on every edge on the same branch as the given node.  A pre-order traversal of the graph.
	 * @param start The node to start at.
	 * @param action The action to perform on every connection between nodes.
	 */
	public static void traverseFromNode(Node start, BiConsumer<Node, Node> action, int expectedSize) {
		Set<Node> alreadyTraversed = new ObjectOpenHashSet<>(expectedSize);
		
		forEachEdgeRecursive(start, action, alreadyTraversed);
	}
	
	
	
	
	/**
	 * Traverse through the graph by connected edges starting from the given node.  Recursive.
	 *
	 * @param currentNode The current node being checked.
	 * @param action The action to perform on every node.
	 * @param alreadyChecked Set of nodes we've already looked at. Pass in a {@code new HashSet<>()} or something sized to the expected number of nodes you'll encounter.
	 */
	private static void forEachNodeRecursive(Node currentNode, Consumer<Node> action, Set<Node> alreadyChecked) {
		action.accept(currentNode);
		alreadyChecked.add(currentNode);
		
		for (Node connectedNode : currentNode.connections) {
			if (!alreadyChecked.contains(connectedNode)) {
				forEachNodeRecursive(connectedNode, action, alreadyChecked);
			}
		}
	}
	
	/**
	 * Traverse through the graph by connected edges starting from the given node.  Recursive.
	 *
	 * @param currentNode The current node being checked.
	 * @param action The action to perform on every node.
	 * @param alreadyChecked Set of nodes we've already looked at. Pass in a {@code new HashSet<>()} or something sized to the expected number of nodes you'll encounter.
	 */
	private static void forEachEdgeRecursive(Node currentNode, BiConsumer<Node, Node> action, Set<Node> alreadyChecked) {
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
		
		getAllConnections().stream()
		                   .map(INBTSerializable::serializeNBT)
		                   .forEach(list::appendTag);
		
		NBTTagCompound compound = new NBTTagCompound();
		compound.setTag("Network", list);
		return compound;
	}
	
	@Override
	public void deserializeNBT(NBTTagCompound tag) {
		this.nodeLookupMap.clear();
		
		NBTTagList list = tag.getTagList("Network", 10);
		for (int i = 0; i < list.tagCount(); i++) {
			ConnectionPair pair = new ConnectionPair();
			pair.readFromNBT(list.getCompoundTagAt(i));
			addConnection(pair);
		}
	}
	
	/**
	 * Connects all given nodes to each other.
	 */
	public static void connectNodes(Set<Node> nodes) {
		if (nodes.size() <= 1) {
			return;
		}
		
		Node[] arr = nodes.toArray(new Node[0]);
		if (arr.length == 2) {
			Node.formTwoWayConnection(arr[0], arr[1]);
		} else {
			// do the thing for every node pair
			for (int i = 0; i < arr.length; i++) {
				for (int j = i + 1; j < arr.length; j++) {
					Node.formTwoWayConnection(arr[i], arr[j]);
				}
			}
		}
		
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
					                    .map(connected -> String.format("(%d %d %d)", connected.pos.getX(), connected.pos.getY(), connected.pos.getZ()))
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
		
		public static void formTwoWayConnection(Node first, Node second) {
			if (Objects.equals(first, second))
				return;
			
			first.connections.add(second);
			second.connections.add(first);
		}
		
		/**
		 * Removes the two-way connection between the provided nodes.
		 * @return true if the nodes were connected, false otherwise.
		 */
		public static boolean deleteTwoWayConnection(@Nonnull Node first, @Nonnull Node second) {
			boolean wasPresent = first.connections.remove(second);
			return second.connections.remove(first) && wasPresent;
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
		
		public Set<IConnectionPair> getConnectionsAsPairs() {
			Set<IConnectionPair> ret = new ObjectOpenHashSet<>();
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
