package org.bimserver.tests.lowlevel;

import static org.junit.Assert.fail;

import org.bimserver.interfaces.objects.SProject;
import org.bimserver.plugins.services.BimServerClientInterface;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.shared.interfaces.bimsie1.Bimsie1LowLevelInterface;
import org.bimserver.tests.utils.TestWithEmbeddedServer;
import org.junit.Test;

public class CreateUnknownType extends TestWithEmbeddedServer {

	@Test
	public void test() {
		try {
			// Create a new BimServerClient with authentication
			BimServerClientInterface bimServerClient = getFactory().create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"));
			
			Bimsie1LowLevelInterface lowLevelInterface = bimServerClient.getBimsie1LowLevelInterface();
			
			// Create a new project
			SProject newProject = bimServerClient.getBimsie1ServiceInterface().addProject("test" + Math.random());
			
			// Start a transaction
			Long tid = lowLevelInterface.startTransaction(newProject.getOid());
			
			lowLevelInterface.createObject(tid, "IfcCartesionPoint"); // IfcCartesi(O)nPoint
			
			// Commit the transaction
			lowLevelInterface.commitTransaction(tid, "test");
		} catch (Exception e) {
			if (e instanceof UserException) {
				
			} else {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}
}