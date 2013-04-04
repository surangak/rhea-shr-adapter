package org.openmrs.module.rheashradapter.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.SessionFactory;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.api.context.Context;
import org.openmrs.module.rheashradapter.hibernate.LogEncounterDAO;
import org.openmrs.module.rheashradapter.hibernate.PatientMergeDAO;
import org.openmrs.module.rheashradapter.model.MergedDataObject;
import org.openmrs.module.rheashradapter.model.PatientMergeRecord;
import org.openmrs.module.rheashradapter.model.PatientRestoreRecord;
import org.springframework.beans.factory.annotation.Autowired;

public class PatientMergeServiceImpl implements PatientMergeService {

	/** The log. */
	protected final Log log = LogFactory.getLog(this.getClass());

	/** The log encounter dao. */
	private PatientMergeDAO patientMergeDAO;

	/** The session factory. */
	@Autowired
	private SessionFactory sessionFactory;

	private LogEncounterDAO logEncounterDAO;

	/**
	 * Instantiates a new log encounter service impl.
	 */
	public PatientMergeServiceImpl() {
		super();
	}

	public void onShutdown() {
		// TODO Auto-generated method stub

	}

	public void onStartup() {
		// TODO Auto-generated method stub

	}

	public String mergePatient(String patientIdentifierType,
			String survivingPatient, String retiringPatient) {
		PatientMergeRecord patientMergeRecord = new PatientMergeRecord();

		try {

			patientMergeRecord.setSurvivingPatientId(survivingPatient);
			patientMergeRecord.setRetiredPatientId(retiringPatient);
			patientMergeRecord.setLogTime(new Date());
			patientMergeRecord
					.setUserId(Context.getAuthenticatedUser().getId());

			SortedSet<MergedDataObject> mergedDataObjects = new TreeSet<MergedDataObject>();

			PatientIdentifierType identifierType = Context.getPatientService()
					.getPatientIdentifierTypeByName(patientIdentifierType);
			List<PatientIdentifierType> identifiers = new ArrayList<PatientIdentifierType>();
			identifiers.add(identifierType);
			List<Patient> patientsToKeep = Context.getPatientService()
					.getPatients(null, survivingPatient, identifiers, false);
			List<Patient> patientsToRetire = Context.getPatientService()
					.getPatients(null, retiringPatient, identifiers, false);
			if (patientsToKeep == null) {
				return "404";
			}
			if (patientsToRetire == null) {
				return "404";
			}

			Patient mainPatient = null;
			Patient duplicatePatient = null;

			if(!patientsToKeep.isEmpty())
				mainPatient = patientsToKeep.get(0);
			else
				return "404";
			if(!patientsToRetire.isEmpty())
			    duplicatePatient = patientsToRetire.get(0);
			else
				return "404";
			
			patientMergeRecord.setSurvivingPatient(mainPatient.getPatientId());
			patientMergeRecord.setRetiredPatient(duplicatePatient
					.getPatientId());

			List<Encounter> encountersToMove = Context.getEncounterService()
					.getEncounters(duplicatePatient);

			if (encountersToMove != null) {

				for (Encounter e : encountersToMove) {
				
					List<Obs> obsList = new ArrayList<Obs>();

					if (e.getAllObs() != null) {

						Iterator it = e.getAllObs().iterator();

						while (it.hasNext()) {
							obsList.add((Obs) it.next());
						}
					} else {
						System.out.println("No obs were found ");
					}

					e.setPatient(mainPatient);
					e.setPatientId(mainPatient.getPatientId());
					e.setChangedBy(Context.getAuthenticatedUser());

					Context.getEncounterService().updateEncounter(e);
					getSessionFactory().getCurrentSession().flush();
					getSessionFactory().getCurrentSession().evict(e);

					for (Obs o : obsList) {

						o.setPerson((Person) mainPatient);
						o.setChangedBy(Context.getAuthenticatedUser());
						o.setComment("Merged with patient"
								+ mainPatient.getId());

						MergedDataObject mergedDataObject = new MergedDataObject();
						mergedDataObject.setEncounterId(e.getEncounterId());
						mergedDataObject.setObsId(o.getId());
						mergedDataObject
								.setPatientMergeRecord(patientMergeRecord);
						mergedDataObjects.add(mergedDataObject);

						Obs ao = Context.getObsService().saveObs(o,
								"Merged with patient");
						getSessionFactory().getCurrentSession().evict(o);

					}
				}
			} else {
				System.out.println("No encounters to move were found");
			}

			List<Obs> encounterlessObs = Context.getObsService()
					.getObservationsByPerson(patientsToRetire.get(0));

			if (encounterlessObs != null) {
				for (Obs o : encounterlessObs) {
					o.setPatient(mainPatient);
					o.setPerson(mainPatient);

					Context.getObsService().saveObs(o, "Merged with patient");
					getSessionFactory().getCurrentSession().evict(o);

					MergedDataObject mergedDataObject = new MergedDataObject();

					mergedDataObject
							.setPatientMergeRecord(patientMergeRecord);
					mergedDataObject.setObsId(o.getId());
					mergedDataObjects.add(mergedDataObject);

				}
			}

			Context.getPatientService().voidPatient(duplicatePatient,
					"Merged with patient " + mainPatient.getId());

			patientMergeRecord.setMergedDataObjects(mergedDataObjects);

			patientMergeRecord.setStatus("success");
			patientMergeDAO.savePatientMergeRecord(patientMergeRecord);

		} catch (Exception e) {
			e.printStackTrace();
			patientMergeRecord.setStatus("error");
			patientMergeDAO.savePatientMergeRecord(patientMergeRecord);
		}
		return "200";

	}

	/**
	 * Gets the session factory.
	 * 
	 * @return the session factory
	 */
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	/**
	 * Sets the session factory.
	 * 
	 * @param sessionFactory
	 *            the new session factory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	public void setPatientMergeDAO(PatientMergeDAO patientMergeDAO) {
		this.patientMergeDAO = patientMergeDAO;
	}

	public PatientMergeDAO getpatientMergeDAO() {
		return patientMergeDAO;
	}

	@Override
	public String restorePatient(String patientIdentifierType,
			String restorePatient) {
		PatientRestoreRecord patientRestoreRecord = new PatientRestoreRecord();
		try {
			patientRestoreRecord.setRetiredPatientId(restorePatient);
			patientRestoreRecord.setLogTime(new Date());
			PatientMergeRecord patientMergeRecord = patientMergeDAO
					.getPatientMergeRecord(restorePatient);
			SortedSet<MergedDataObject> mergedDataObjects = new TreeSet<MergedDataObject>();
			
			mergedDataObjects = (SortedSet)patientMergeRecord.getMergedDataObjects();
			
			SortedSet<MergedDataObject> newEncounterDataObjects = new TreeSet<MergedDataObject>();
			Iterator iterator = mergedDataObjects.iterator();
			while (iterator.hasNext()){
				MergedDataObject mergedDataObject = new MergedDataObject();
				mergedDataObject = (MergedDataObject) iterator.next();
				newEncounterDataObjects.add(mergedDataObject);
			}
			
			Patient p = patientMergeDAO.getRetiredPatient(patientMergeRecord
					.getRetiredPatient());
			
			if (p == null) {
				return "404";
			}else{
				patientRestoreRecord.setRetiredPatient(p.getPatientId());
			}
			Patient restoredPatient = p;
			Context.getPatientService().unvoidPatient(restoredPatient);
			Set encounters = new HashSet();
			
			for (MergedDataObject mergedDataObject : patientMergeRecord
					.getMergedDataObjects()) {
				Obs obs = null;
				if (mergedDataObject.getEncounterId() != null) {
					encounters.add((int) mergedDataObject.getEncounterId());
				} else {
					int obsId = mergedDataObject.getObsId();
					obs = Context.getObsService().getObs(obsId);

					obs.setPatient(restoredPatient);
					obs.setPerson(restoredPatient);
					
					Context.getObsService().saveObs(obs,
							"restored with patient");
					getSessionFactory().getCurrentSession().evict(obs);

				}

			}

			Iterator it = encounters.iterator();
			while (it.hasNext()) {
				int i = (Integer) it.next();

				Encounter encounter = Context.getEncounterService()
						.getEncounter(i);

				encounter.setPatient(restoredPatient);
				encounter.setPatientId(restoredPatient.getPatientId());

				Context.getEncounterService().updateEncounter(encounter);
				getSessionFactory().getCurrentSession().flush();
				getSessionFactory().getCurrentSession().evict(encounter);

				Set<Obs> obsList = encounter.getAllObs();
				Iterator<Obs> itr = obsList.iterator();
				while (itr.hasNext()) {
					Obs obs = itr.next();
					
					obs.setPatient(restoredPatient);
					obs.setPerson((Person) restoredPatient);
					Context.getObsService().saveObs(obs, "Merged with patient");
					getSessionFactory().getCurrentSession().evict(obs);

				}
			}
			
			patientRestoreRecord.setMergedDataObjects(newEncounterDataObjects);
			patientRestoreRecord.setStatus("success");
			patientRestoreRecord.setUserId(Context.getAuthenticatedUser().getUserId());
			patientMergeDAO.savePatientRestore(patientRestoreRecord);
		} catch (Exception e) {
			patientRestoreRecord.setStatus("error");
			patientMergeDAO.savePatientRestore(patientRestoreRecord);
		}

		return "200";
	}
}
