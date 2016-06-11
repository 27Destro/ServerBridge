/*******************************************************************************
 * MIT License
 *
 * Copyright (c) 2016 Valentin 'ThisIsMac' Marchaud
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *******************************************************************************/
package fr.vmarchaud.mineweb.common;

import fr.vmarchaud.mineweb.common.injector.router.RouteMatcher;

public interface ICore {
	
	/**
	 * Get the router that handle http connection
	 * @return RouteMatcher
	 */
	public RouteMatcher		getHTTPRouter();
	
	/**
	 * Get the game server instance
	 * @return ProxyServer for bungee OR Server for bukkit
	 */
	public Object			getGameServer();
	
	/**
	 * Get the plugin instance
	 * @return Plugin instance for bungee OR JavaPlugin for bukkit
	 */
	public Object			getPlugin();
	
	/**
	 * Get the type of plugin that is running
	 * @return EnumPluginType
	 */
	public EnumPluginType	getType();
	
	
	public enum EnumPluginType {
		BUKKIT, BUNGEE;
	}
}
