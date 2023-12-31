package uk.ac.starlink.splat.vamdc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.vamdc.xsams.io.JAXBContextFactory;
import org.vamdc.xsams.schema.AtomType;
import org.vamdc.xsams.schema.AtomicIonType;
import org.vamdc.xsams.schema.AtomicStateType;
import org.vamdc.xsams.schema.Atoms;
import org.vamdc.xsams.schema.ChemicalElementType;
import org.vamdc.xsams.schema.DataType;
import org.vamdc.xsams.schema.IsotopeType;
import org.vamdc.xsams.schema.MolecularChemicalSpeciesType;
import org.vamdc.xsams.schema.MolecularStateCharacterisationType;
import org.vamdc.xsams.schema.MolecularStateType;
import org.vamdc.xsams.schema.MoleculeType;
import org.vamdc.xsams.schema.RadiativeTransitionProbabilityType;
import org.vamdc.xsams.schema.RadiativeTransitionType;
import org.vamdc.xsams.schema.ValueType;
import org.vamdc.xsams.schema.WlType;
import org.vamdc.xsams.schema.XSAMSData;


import jsky.util.Logger;
import uk.ac.starlink.splat.data.ssldm.Level;
import uk.ac.starlink.splat.data.ssldm.SpectralLine;

/**
 * 
 * XSAMSParser 
 * @author mm
 *
 */
public class XSAMSParser  {



    XSAMSData xsams;

    public XSAMSParser(InputStream inps) throws JAXBException, Exception {

        try {

            xsams = (XSAMSData)JAXBContextFactory.getUnmarshaller().unmarshal(inps);
        } catch (JAXBException e) {
            // TODO Auto-generated catch block
            Logger.info(this, "JABException when parsing XSAMS input ");
            //e.printStackTrace();
            throw e;
        } catch (Exception e) {
            Logger.info(this, "Exception when parsing XSAMS input: "+e.getMessage());
            throw e;
        }


    }
    // to do : constructors with other kind of input parameters (file name, url, etc)

    /*
     * Reads the XSAMSData returned from a VAMDC Database, and transform it to
     * SpectralLine objects
     */
    public ArrayList<SpectralLine> getSpectralLines() {

        ArrayList<SpectralLine> lines = new ArrayList<SpectralLine>();

        HashMap <String,String> elements= new HashMap<String,String>();

        // get the atom/species symbol and put into a hashmap
        List<AtomType> atoms = null;
        List<MoleculeType> molecules = null;
        try {
            atoms = xsams.getSpecies().getAtoms().getAtoms();

            for (AtomType atom : atoms) {

            	//  System.out.println("Atom: "+ atom.getChemicalElement().getElementSymbol() + " - ");
            	//  System.out.println( "Charge: "+ atom.getChemicalElement().getNuclearCharge() + " - ");

            	for (IsotopeType iso : atom.getIsotopes()) {
            		//    IsotopeParametersType isop = iso.getIsotopeParameters();
            		for (AtomicIonType ion:iso.getIons()) {
            			System.out.println("Ion: "+ ion.getIonCharge() + " - "+ion.getSpeciesID());
            			String symbol="";
            			try {
            				symbol = atom.getChemicalElement().getElementSymbol().value();
            			} catch ( NullPointerException e) {
            				/// sometimes element symbol is null
            				symbol = "";
            			}

            			System.out.println("Atom: "+ symbol);
            			elements.put(ion.getSpeciesID(), symbol);
            		}
            	}
            }
        } catch (NullPointerException npe) {

        }
        try {
        	molecules = xsams.getSpecies().getMolecules().getMolecules();

        	for (MoleculeType molecule : molecules) {

        		System.out.println("Molecule: "+ molecule.getMolecularChemicalSpecies().getStoichiometricFormula() + " - ");
        		//  System.out.println( "Charge: "+ atom.getChemicalElement().getNuclearCharge() + " - ");

        		//          for (IsotopeType iso : atom.getIsotopes()) {
        		//    IsotopeParametersType isop = iso.getIsotopeParameters();
        		//              for (AtomicIonType ion:iso.getIons()) {
        		//                 System.out.println("Ion: "+ ion.getIonCharge() + " - "+ion.getSpeciesID());
        		String symbol="";
        		try {
        			symbol = molecule.getMolecularChemicalSpecies().getStoichiometricFormula();
        		} catch ( NullPointerException e) {
        			/// sometimes element symbol is null
        			symbol = "";
        		}

        		System.out.println("Molecule: "+ symbol);
        		elements.put(molecule.getSpeciesID(), symbol);
        		//            }
        		//        }
        	}
        } catch (NullPointerException npe) {

        }
        if (molecules == null && atoms == null) {
        	return lines;
        }

        for ( RadiativeTransitionType radtrans: xsams.getProcesses().getRadiative().getRadiativeTransitions() ) { 

            Level initialLevel=null;
            Level finalLevel = null;
            boolean atom=false;
            SpectralLine line=null;
            //SpeciesType specref = (SpeciesType) radtrans.getSpeciesRef();
            // SpeciesStateRefType spectype = (SpeciesStateRefType) radtrans.getSpeciesRef();
            if (radtrans.getLowerStateRef() != null && radtrans.getLowerStateRef().getClass().equals(MolecularStateType.class)) {
                atom=false;

                MolecularStateType state1 = (MolecularStateType) radtrans.getLowerStateRef();    
                MolecularStateType state2 = (MolecularStateType) radtrans.getUpperStateRef();  

                MoleculeType molecule1 = (MoleculeType) state1.getParent();
                MoleculeType molecule2 = (MoleculeType) state2.getParent();
                String id1 = molecule1.getMolecularChemicalSpecies().getStoichiometricFormula();
                String id2 = molecule2.getMolecularChemicalSpecies().getStoichiometricFormula();
               // int charge1 = molecule1.getMolecularChemicalSpecies().getIonCharge();

                String desc1 = state1.getDescription();
                String desc2 = state2.getDescription();

                MolecularStateCharacterisationType charac1 = state1.getMolecularStateCharacterisation();
                MolecularStateCharacterisationType charac2 = state2.getMolecularStateCharacterisation();

                DataType energy1 = charac1.getStateEnergy();
                DataType energy2 = charac2.getStateEnergy();

                //Integer statWeight = charac1.getTotalStatisticalWeight();

                initialLevel = new Level();
                if (energy1 != null) {
                    initialLevel.setEnergy(energy1.getValue().getValue(), energy1.getValue().getUnits());                         
                } 
                line = initialiseLine(atom, desc1, desc2, energy1, energy2, id1, id2, -1, null, null, null, null );                    

            }
            else if (radtrans.getLowerStateRef() != null && radtrans.getLowerStateRef().getClass().equals(AtomicStateType.class)) {
                atom=true;
                AtomicStateType state1 = (AtomicStateType) radtrans.getLowerStateRef();         
                AtomicStateType state2 = (AtomicStateType) radtrans.getUpperStateRef();

                DataType energy1 = state1.getAtomicNumericalData().getStateEnergy();
                DataType energy2 = state2.getAtomicNumericalData().getStateEnergy();

                // state2.getAtomicNumericalData().getIonizationEnergy();
                String description1 = state1.getDescription();
                String description2 = state2.getDescription();

                AtomicIonType ion1 = (AtomicIonType) state1.getParent();    
                AtomicIonType ion2 =  (AtomicIonType) state2.getParent();                   

                String id1, id2;
                try {
                    id1= ion1.getIsoelectronicSequence().value();
                } catch (Exception e ) {
                     id1 = elements.get(ion1.getSpeciesID()); //+" "+ion1.getIonCharge();
                }
                try {
                    id2= ion2.getIsoelectronicSequence().value();
                } catch (Exception e ) {
                     id2 = elements.get(ion2.getSpeciesID()); //+" "+ion1.getIonCharge();
                }
                    

                int ionCharge = ion1.getIonCharge();
                Double statWeight1 = state1.getAtomicNumericalData().getStatisticalWeight();
                Double statWeight2 = state1.getAtomicNumericalData().getStatisticalWeight();

                DataType lande1 = state1.getAtomicNumericalData().getLandeFactor();
                DataType lande2 = state2.getAtomicNumericalData().getLandeFactor();

                line = initialiseLine(atom, description1, description2, energy1, energy2, id1, id2, ionCharge, statWeight1, statWeight2, lande1, lande2 );                    

            } else {
               continue;
            }
            try {
                RadiativeTransitionProbabilityType prob = radtrans.getProbabilities().get(0);
                DataType os = prob.getOscillatorStrength();;
                if (os != null) {
                    line.setOscillatorStrength( os.getValue().getValue(), os.getValue().getUnits());
                }
                os = prob.getWeightedOscillatorStrength();
                if (os != null) {
                    line.setWeightedOscillatorStrength( os.getValue().getValue(), os.getValue().getUnits());
                }
                os = prob.getTransitionProbabilityA();
                if (os != null) {
                    line.setEinsteinA( os.getValue().getValue(), os.getValue().getUnits());
                }
            } catch (Exception e) {

            }

            //   String elSymbol=null;
            //   if (id != null)
            //       elSymbol=elements.get(id);
           
            try {
              
            		WlType wl =  radtrans.getEnergyWavelength().getWavelengths().get(0);
            		String unit = wl.getValue().getUnits();

            		if (unit.equals("A"))
            			unit="Angstrom"; // correct unit for AST 
            		if (wl.isVacuum()) { // ?!!!!!! check if it's correct
            			line.setWavelength(wl.getValue().getValue(), unit);

            		} else {
            			line.setAirWavelength(wl.getValue().getValue(), unit);                          
            			line.setWavelength(wl.getValue().getValue()*wl.getAirToVacuum().getValue().getValue(), unit);
            		}
            	
            } catch (Exception e) {
            	// if no wavelenghts are present, try  wavenumbers instead
            	try {
            		DataType wn = radtrans.getEnergyWavelength().getWavenumbers().get(0);
            		Double wavelength = 1/wn.getValue().getValue();
            		String unit = wn.getValue().getUnits();
            		unit = unit.replaceAll("1/", "");//!!!
            		System.out.println("WaveNumber: "+wn.getValue().getValue()+" "+wn.getValue().getUnits() + " Wavelength: "+wavelength+" "+unit);
            		line.setWavelength(wavelength, unit);  
            		
            	}
            	catch (Exception ee) {

            	}
            }
            /*                try {
                    String e1 = null;
                    String e2 = null;
                   if ( line.getInitialLevel().getEnergy() != null ) 
                        e1 = line.getInitialLevel().getEnergy().getString();
                    if ( line.getFinalLevel().getEnergy() != null ) 
                        e2 = line.getFinalLevel().getEnergy().getString();

                    //              System.out.println(line.getTitle()+" wl "+ line.getWavelength().getString()+" "+
                    //                      " e1 "+ e1+" e2 "+e2+" "+
                    //                     " os "+line.getOscillatorStrength().getString()+" wos "+line.getWeightedOscillatorStrength().getString());                    
                }catch(Exception e) {
                    System.out.println(">>>>>>>>>>>>>>>>>"+line.getTitle()+" wl "+ line.getWavelength().getString());
                }
             */
            lines.add(line);
        // System.out.println(elSymbol+" wl "+ wl.getValue().getValue() + " "+ wl.getValue().getUnits()+" os "+osValue+" "+osUnit);      
        } // for radtrans ... 
       return lines;
    }



    private SpectralLine initialiseLine(boolean atom, String description1, String description2, DataType energy1, DataType energy2, String id1, String id2, int ionCharge, Double statWeight1,
            Double statWeight2, DataType lande1, DataType lande2) {

        SpectralLine line = new SpectralLine();

        if (id1 != null) {
            if (atom) 
                line.setInitialElement(id1, ionCharge);
            else 
                line.setInitialElement(id1, -1);
        } 

        if (id2 != null)
            line.setFinalElement(id2);

        Level initial = new Level();

        if (energy1 != null) {
            initial.setEnergy(energy1.getValue().getValue(), energy1.getValue().getUnits());                         
        } 
        try {
            initial.setConfiguration(description1);
            initial.setTotalStatWeight(statWeight1, null) ; // An integer representing the total number of terms pertaining to a given level
        } catch( Exception e) {}
        //PhysicalQuantity nuclearStatWeight ; // The same as Level.totalStatWeight for nuclear spin states only
        try {
            initial.setLandeFactor(lande1.getValue().getValue(), (Double) null, lande1.getValue().getUnits()); // A dimensionless factor g that accounts for the splitting of normal energy levels into uniformly spaced sublevels in the presence of a magnetic field
        }catch( Exception e) {}

        Level finalLevel = new Level();

        if (energy2 != null) {
            finalLevel.setEnergy(energy2.getValue().getValue(), energy2.getValue().getUnits());
        } else {
            finalLevel.setEnergy(null);
        }
        try {
            finalLevel.setConfiguration(description2);
            finalLevel.setTotalStatWeight(statWeight2, null) ; // An integer representing the total number of terms pertaining to a given level
        }catch( Exception e) {}
        //PhysicalQuantity nuclearStatWeight ; // The same as Level.totalStatWeight for nuclear spin states only
        try {
            finalLevel.setLandeFactor(lande2.getValue().getValue(), (Double) null, lande2.getValue().getUnits()); ;// A dimensionless factor g that accounts for the splitting of normal energy levels into uniformly spaced sublevels in the presence of a magnetic field
        }catch( Exception e) {}
        //initial.setLifeTime(state1.getAtomicNumericalData().getLifeTimes());
        //PhysicalQuantity lifeTime ; // Intrinsic lifetime of a level due to its radiative decay
        //PhysicalQuantity energy  ; //The binding energy of an electron belonging to the level

        //String energyOrigin ; // Human readable string indicating the nature of the energy origin, e.g., âIonization energy limitâ, âGround state energyâ of an atom, âDissociation limitâ for  a molecule, etc
        //QuantumState quantumState ; // A representation of the level quantum state through its set of quantum numbers
        //String nuclearSpinSymmetryType ;//  A string indicating the type of nuclear spin symmetry. Possible values are: âparaâ,âorthoâ, âmetaâ
        //PhysicalQuantity parity ;//  Eigenvalue of the parityoperator. Values (+1,-1)
        //String configuration ;
        line.setInitialLevel(initial);
        line.setFinalLevel(finalLevel);
        //  line.setIntensity(intensity);
        //!!! is it possible to have more than one in our case?
        return line;

    }
}

