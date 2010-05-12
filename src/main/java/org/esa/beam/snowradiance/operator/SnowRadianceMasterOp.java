package org.esa.beam.snowradiance.operator;

import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.datamodel.Product;

import java.util.Map;
import java.util.HashMap;

/**
 * Snow radiance 'master operator' (CURRENTLY NOT USED)
 *
 * @author Olaf Danne
 * @version $Revision: 8267 $ $Date: 2010-02-05 16:39:24 +0100 (Fr, 05 Feb 2010) $
 */
@OperatorMetadata(alias="SnowRadiance.Master")
public class SnowRadianceMasterOp extends Operator {

    @SourceProduct(alias = "sourceMeris",
                   label = "Name (MERIS product)",
                   description = "Select a MERIS product for snow grains retrieval.")
    private Product merisProduct;

    @SourceProduct(alias = "sourceAatsr",
                   label = "Name (MERIS product)",
                   description = "Select a MERIS product for snow grains retrieval.")
    private Product aatsrProduct;


    // Target bands
    @Parameter(defaultValue = "false",
               description = "Copy input bands to target product",
               label = "Copy input bands")
    private boolean copyInputBands;

    @Parameter(defaultValue = "true",
               description = "Compute Snow Grain Size",
               label = "Compute snow grain size")
    private boolean computeSnowGrainSize;

    @Parameter(defaultValue = "true",
               description = "Compute snow albedo",
               label = "Compute snow albedo")
    private boolean computeSnowAlbedo;

    @Parameter(defaultValue = "true",
               description = "Compute snow soot content",
               label = "Compute snow soot content")
    private boolean computeSnowSootContent;

    @Parameter(defaultValue = "true",
               description = "Compute Snow Temperature (FUB)",
               label = "Compute Snow Temperature (FUB)")
    private boolean computeSnowTemperatureFub;

    @Parameter(defaultValue = "true",
               description = "Compute Emissivity (FUB)",
               label = "Compute Emissivity (FUB)")
    private boolean computeEmissivityFub;


    // complementary quantities:
    @Parameter(defaultValue = "false",
               description = "Compute MERIS water vapour",
               label = "Compute MERIS water vapour")
    private boolean computeMerisWaterVapour;

    @Parameter(defaultValue = "false",
               description = "Compute MERIS NDVI",
               label = "Compute MERIS NDVI")
    private boolean computeMerisNdvi;

    @Parameter(defaultValue = "false",
               description = "Compute MERIS NDSI",
               label = "Compute MERIS NDSI")
    private boolean computeMerisNdsi;

    @Parameter(defaultValue = "false",
               description = "Compute MERIS MDSI",
               label = "Compute MERIS MDSI")
    private boolean computeMerisMdsi;

    @Parameter(defaultValue = "false",
               description = "Copy AATSR L1 flags",
               label = "Copy AATSR L1 flags")
    private boolean copyAatsrL1Flags;


    // Processing parameters
    @Parameter(defaultValue = "true",
               description = "Apply cloud mask",
               label = "Apply cloud mask")
    private boolean applyCloudMask;

//    @Parameter(defaultValue = "false",
//               description = "Get cloud mask from cloud probability (MEPIX)",
//               label = "Cloud probability (MEPIX)")
//    private boolean getCloudMaskFromMepix;

    @Parameter(defaultValue = "true",
               description = "Get cloud mask from feature classification (MERIS/AATSR Synergy)",
               label = "Cloud probability (MERIS/AATSR Synergy)")
    private boolean getCloudMaskFromSynergy;

    @Parameter(defaultValue = "true",
               description = "Apply 100% snow mask",
               label = "Apply 100% snow mask")
    private boolean apply100PercentSnowMask;

    @Parameter(defaultValue = "false",
               description = "Apply 100% snow mask with AATSR as master",
               label = "AATSR as master")
    private boolean use100PercentSnowMaskWithAatsrMaster;

//    @Parameter(defaultValue = "false",
//               description = "Apply 100% snow mask with MERIS as master",
//               label = "MERIS as master")
//    private boolean use100PercentSnowMaskWithMerisMaster;

    @Parameter(defaultValue = "0.99", interval = "[0.0, 1.0]",
               description = "Assumed emissivity at 11 microns",
               label = "Assumed emissivity at 11 microns")
    private double assumedEmissivityAt11Microns;

    @Parameter(defaultValue = "0.8", interval = "[0.0, 1.0]",
               description = "Cloud probability threshold",
               label = "Cloud probability threshold")
    private double cloudProbabilityThreshold;

    @Parameter(defaultValue = "0.8", interval = "[0.0, 1.0]",
               description = "NDSI upper threshold",
               label = "NDSI upper threshold")
    private double ndsiUpperThreshold;

    @Parameter(defaultValue = "0.8", interval = "[0.0, 1.0]",
               description = "NDSI lower threshold",
               label = "NDSI lower threshold")
    private double ndsiLowerThreshold;

    @Parameter(defaultValue = "0.8", interval = "[0.0, 1.0]",
               description = "AATSR 1610um upper threshold",
               label = "AATSR 1610um upper threshold")
    private double aatsr1610UpperThreshold;

    @Parameter(defaultValue = "0.8", interval = "[0.0, 1.0]",
               description = "AATSR 1610um lower threshold",
               label = "AATSR 1610um lower threshold")
    private double aatsr1610LowerThreshold;






//    @SourceProduct(alias = "source",
//                   label = "Name (MERIS/AATSR colocated product)",
//                   description = "Select a MERIS/AATSR colocated product for snow temperature retrieval.")
//    private Product colocatedProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

//    @Parameter(defaultValue = "true",
//               description = "Compute Snow Temperature (FUB)",
//               label = "Compute Snow Temperature (FUB)")
//    private boolean computeSnowTemperatureFub;
//
//    @Parameter(defaultValue = "true",
//               description = "Compute Emissivity (FUB)",
//               label = "Compute Emissivity (FUB)")
//    private boolean computeEmissivityFub;
//
//    @Parameter(defaultValue = "true",
//               description = "Compute Unpolluted Snow Grain Size",
//               label = "Compute Unpolluted Snow Grain Size")
//    private boolean computeUnpollutedSnowGrainSize;
//
//    @Parameter(defaultValue = "false",
//               description = "Compute Polluted Snow Grain Size",
//               label = "Compute Polluted Snow Grain Size")
//    private boolean computePollutedSnowGrainSize;
//
//    @Parameter(defaultValue = "true",
//               description = "Compute Snow Albedo",
//               label = "Compute Snow Albedo")
//    private boolean computeSnowAlbedo;
//
//    @Parameter(defaultValue = "M",
//               description = "Master Product Bands Suffix",
//               label = "Master Product Bands Suffix")
//    private String masterProductBandsSuffix;
//
//    @Parameter(defaultValue = "S",
//               description = "Slave Product Bands Suffix",
//               label = "Slave Product Bands Suffix")
//    private String slaveProductBandsSuffix;
//
//    @Parameter(alias = SnowRadianceConstants.LUT_PATH_PARAM_NAME,
//               defaultValue = SnowRadianceConstants.LUT_PATH_PARAM_DEFAULT,
//               description = SnowRadianceConstants.LUT_PATH_PARAM_DESCRIPTION,
//               label = SnowRadianceConstants.LUT_PATH_PARAM_LABEL)
//    private String lutPath;


    public void initialize() throws OperatorException {

        System.out.println("bla");
        // compute the  FUB product...
        Product fubSnowTempProduct = null;
//        if (computeSnowTemperatureFub || computeEmissivityFub) {
//            Map<String, Product> fubSnowTempInput = new HashMap<String, Product>(1);
//            fubSnowTempInput.put("source", colocatedProduct);
//            Map<String, Object> fubSnowTempParams = new HashMap<String, Object>(4);
//            fubSnowTempParams.put("computeSnowTemperatureFub", computeSnowTemperatureFub);
//            fubSnowTempParams.put("masterProductBandsSuffix", masterProductBandsSuffix);
//            fubSnowTempParams.put("slaveProductBandsSuffix", slaveProductBandsSuffix);
//            fubSnowTempProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(SnowTemperatureEmissivityOp.class), fubSnowTempParams, fubSnowTempInput);
//        }

        // compute the  snow grains product...
        Product snowGrainsProduct = null;
//        if (computeUnpollutedSnowGrainSize || computePollutedSnowGrainSize || computeSnowAlbedo) {
//            Map<String, Product> snowGrainsInput = new HashMap<String, Product>(1);
//            snowGrainsInput.put("source", merisProduct);
//            Map<String, Object> snowGrainsParams = new HashMap<String, Object>(4);
//            snowGrainsParams.put("computeUnpollutedSnowGrainSize", computeUnpollutedSnowGrainSize);
//            snowGrainsParams.put("computePollutedSnowGrainSize", computePollutedSnowGrainSize);
//            snowGrainsParams.put("computeSnowAlbedo", computeSnowAlbedo);
//            fubSnowTempProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(SnowGrainSizePollutionOp.class), snowGrainsParams, snowGrainsInput);
//        }

//        targetProduct = fubSnowTempProduct;
        targetProduct = snowGrainsProduct;
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(SnowRadianceMasterOp.class);
        }
    }
}
