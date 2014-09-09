package com.mithion.griefguardian.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;

import com.mithion.griefguardian.util.PlayerDataUtils;
import com.mithion.griefguardian.util.WarpPoint;

public class Warp extends CommandBase {

	@Override
	public String getCommandName() {
		return "warp";
	}

	@Override
	public String getCommandUsage(ICommandSender commandSender) {
		return "/warp [target] [SET <name> [GLOBAL]] <TO> (<WP warpname> || <x y z [dimension]> || <targetplayer [SPAWN]> || SPAWN || WORLDSPAWN)";
	}

	@Override
	public void processCommand(ICommandSender commandSender, String[] args) {
		int argIndex = 0;

		String warpName = "";
		boolean saveWarpLoc = false;
		boolean globalWarp = false;

		EntityPlayerMP warpTarget = null;		

		if (args[argIndex].toUpperCase().equals("SET") || args[argIndex].toUpperCase().equals("TO"))
			warpTarget = getCommandSenderAsPlayer(commandSender);	
		else
			warpTarget = getPlayer(commandSender, args[argIndex++]);


		if (args[argIndex].toUpperCase().equals("SET")){
			argIndex++;
			saveWarpLoc = true;
			warpName = args[argIndex++];
			if (args[argIndex].toUpperCase().equals("GLOBAL")){
				argIndex++;
				globalWarp = true;
			}
		}

		//'TO' is necessary to know when we're starting to parse the destination
		if (!args[argIndex++].toUpperCase().equals("TO"))
			throw new WrongUsageException(getCommandUsage(commandSender));

		WarpPoint warpPoint = null;

		if (args[argIndex].toUpperCase().equals("SPAWN")){ //SPAWN keyword
			//resolve the warp target's spawn point for the current dimension
			warpPoint = getWarpPoint(warpTarget, true);
		}else if (args[argIndex].toUpperCase().equals("WORLDSPAWN")){ //SPAWN keyword
			//resolve the spawn point for the current dimension
			ChunkCoordinates spawnCoords = warpTarget.worldObj.provider.getSpawnPoint();
			warpPoint = new WarpPoint(spawnCoords.posX, spawnCoords.posY, spawnCoords.posZ, warpTarget.worldObj.provider.dimensionId);
		}else if (args[argIndex].toUpperCase().equals("WP")){ //looking to warp to a waypoint
			argIndex++;
			//check local waypoints
			warpPoint = PlayerDataUtils.findLocalWarpPoint(warpTarget, args[argIndex]);
			if (warpPoint == null){
				warpPoint = PlayerDataUtils.findGlobalWarpPoint(args[argIndex]);
			}
		}else if (args.length - 1 == argIndex){ //target player
			try{
				//attempt to resolve the specified player by name
				EntityPlayerMP destinationTarget = getPlayer(commandSender, args[argIndex]);
				//found it!  Construct a warp point from the located player.
				warpPoint = new WarpPoint(destinationTarget.posX, destinationTarget.posY, destinationTarget.posZ, destinationTarget.worldObj.provider.dimensionId);
			}catch(Throwable t){
				//couldn't find it...attempt to load their offline data and get the result that way
				warpPoint = getWarpPointFromOfflineData(PlayerDataUtils.loadOfflinePlayerData(args[argIndex]), false);
			}
		}else if (args[argIndex + 1].toUpperCase().equals("SPAWN")){ //target player's spawn
			try{
				//attempt to resolve the specified player by name
				EntityPlayerMP destinationTarget = getPlayer(commandSender, args[argIndex]);
				//found it!  Construct a warp point from the located player's spawn point
				warpPoint = getWarpPoint(destinationTarget, true);
			}catch(Throwable t){
				//couldn't find it...attempt to load their offline data and get the result that way
				getWarpPointFromOfflineData(PlayerDataUtils.loadOfflinePlayerData(args[argIndex]), true);
			}
		}else{ //x,y,z (possibly dimension)
			//parse x, y, z
			double x = parseDouble(commandSender, args[argIndex++]);
			double y = parseDouble(commandSender, args[argIndex++]);
			double z = parseDouble(commandSender, args[argIndex++]);
			//parse dimension if the arg is there, otherwise use the current dim
			int dimension = (argIndex < args.length - 1) ? parseInt(commandSender, args[argIndex]) : warpTarget.worldObj.provider.dimensionId;
			warpPoint = new WarpPoint(x, y, z, dimension);
		}

		//did we find a warp point after all that parsing??
		if (warpPoint != null){
			if (!saveWarpLoc){
				//is a dimension transfer needed??
				if (warpPoint.dimension != warpTarget.worldObj.provider.dimensionId){
					commandSender.addChatMessage(new ChatComponentText("That isn't implemented yet!"));
				}else{ //nope, just update the position
					warpTarget.setPositionAndUpdate(warpPoint.x, warpPoint.y, warpPoint.z);
				}

				this.func_152373_a(commandSender, this, String.format(
						"Warped %s to %.1f, %.1f, %.1f (dim %d)", 
						warpTarget.getCommandSenderName(), 
						warpPoint.x, 
						warpPoint.y, 
						warpPoint.z, 
						warpPoint.dimension));
			}else{				
				if (globalWarp){
					PlayerDataUtils.saveGlobalWarpPoint(warpName, warpPoint);
					this.func_152373_a(commandSender, this, String.format(
							"Saved [%.1f, %.1f, %.1f (dim %d)] as a [GLOBAL] warp point named %s", 							
							warpPoint.x, 
							warpPoint.y, 
							warpPoint.z, 
							warpPoint.dimension,
							warpName,
							warpTarget.getCommandSenderName()));
				}else{
					PlayerDataUtils.saveLocalWarpPoint(warpTarget, warpName, warpPoint);
					this.func_152373_a(commandSender, this, String.format(
							"Saved [%.1f, %.1f, %.1f (dim %d)] as a warp point named %s for %s", 							
							warpPoint.x, 
							warpPoint.y, 
							warpPoint.z, 
							warpPoint.dimension,
							warpName,
							warpTarget.getCommandSenderName()));
				}
			}
		}else{
			commandSender.addChatMessage(new ChatComponentText("griefguardian.commands.warpnotfound"));
		}
	}

	private WarpPoint getWarpPoint(EntityPlayerMP player, boolean spawn){
		if (spawn){
			ChunkCoordinates spawnCoords = player.getBedLocation(player.worldObj.provider.dimensionId);
			if (spawnCoords == null)
				spawnCoords = player.worldObj.provider.getSpawnPoint();

			return new WarpPoint(spawnCoords.posX, spawnCoords.posY, spawnCoords.posZ, player.worldObj.provider.dimensionId);
		}else{
			return new WarpPoint(player.posX, player.posY, player.posZ, player.worldObj.provider.dimensionId);
		}
	}

	private WarpPoint getWarpPointFromOfflineData(NBTTagCompound offlineData, boolean spawn){
		if (offlineData == null)
			return null;
		
		if (spawn){
			double x = offlineData.getInteger("SpawnX");
			double y = offlineData.getInteger("SpawnY");
			double z = offlineData.getInteger("SpawnZ");
			int dim = getDimensionFromWorldName(offlineData.getString("SpawnWorld"));

			return new WarpPoint(x, y, z, dim);
		}else{
			NBTTagList pos = offlineData.getTagList("Pos", 6);
			if (pos == null || pos.tagCount() != 3)
				return null;
			double x = pos.func_150309_d(0);
			double y = pos.func_150309_d(1);
			double z = pos.func_150309_d(2);
			int dim = offlineData.getInteger("Dimension");

			return new WarpPoint(x, y, z, dim);
		}
	}

	private int getDimensionFromWorldName(String worldName){
		for (WorldServer ws : MinecraftServer.getServer().worldServers){
			if (ws.provider.getDimensionName().equals(worldName))
				return ws.provider.dimensionId;
		}
		//default to the overworld just to be safe
		return 0;
	}
}
