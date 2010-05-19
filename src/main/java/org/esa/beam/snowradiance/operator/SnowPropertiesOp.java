package org.esa.beam.snowradiance.operator;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.jnn.JnnException;
import com.bc.jnn.JnnNet;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.BitmaskDef;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.cloud.CloudProbabilityOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.LookupTable;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "SnowRadiance.properties")
public class SnowPropertiesOp extends Operator {
    @SourceProduct(alias = "colocatedProduct",
                   label = "Name (Collocated MERIS AATSR product)",
                   description = "Select a collocated MERIS AATSR product.")
    private Product colocatedProduct;

    @SourceProduct(alias = "merisProduct",
                   label = "Name (MERIS product)",
                   description = "Select a MERIS product.")
    private Product merisProduct;

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
               description = "Compute AATSR NDSI",
               label = "Compute AATSR NDSI")
    private boolean computeAatsrNdsi;

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

    @Parameter(defaultValue = "false",
               description = "Get cloud mask from feature classification (MERIS/AATSR Synergy)",
               label = "Cloud probability (MERIS/AATSR Synergy)")
    private boolean getCloudMaskFromSynergy;

    @Parameter(defaultValue = "true",
               description = "Apply 100% snow mask",
               label = "Apply 100% snow mask")
    private boolean apply100PercentSnowMask;

    @Parameter(defaultValue = "0.99", interval = "[0.0, 1.0]",
               description = "Assumed emissivity at 11 microns",
               label = "Assumed emissivity at 11 microns")
    private double assumedEmissivityAt11Microns;

    @Parameter(defaultValue = "0.8", interval = "[0.0, 1.0]",
               description = "Cloud probability threshold",
               label = "Cloud probability threshold")
    private double cloudProbabilityThreshold;

    @Parameter(defaultValue = "0.96", interval = "[0.0, 1.0]",
               description = "NDSI upper threshold",
               label = "NDSI upper threshold")
    private double ndsiUpperThreshold;

    @Parameter(defaultValue = "0.90", interval = "[0.0, 1.0]",
               description = "NDSI lower threshold",
               label = "NDSI lower threshold")
    private double ndsiLowerThreshold;

    @Parameter(defaultValue = "10.0", interval = "[1.0, 100.0]",
               description = "AATSR 1610nm upper threshold",
               label = "AATSR 1610nm upper threshold")
    private double aatsr1610UpperThreshold;

    @Parameter(defaultValue = "1.0", interval = "[1.0, 100.0]",
               description = "AATSR 1610nm lower threshold",
               label = "AATSR 1610nm lower threshold")
    private double aatsr1610LowerThreshold;


    @TargetProduct(description = "The target product.")
    private Product targetProduct;


    private Product cloudProbabilityProduct;
    private Product cloudScreeningProduct;

    private Band aatsrBt11NadirBand;
    private Band aatsrBt12NadirBand;

    private Band aatsrReflecNadir0670Band;
    private Band aatsrReflecNadir0870Band;
    private Band aatsrReflecNadir1600Band;

    private Band merisRad13Band;
    private Band merisRad14Band;
    private Band merisRad15Band;

    public static final String CLOUDICESNOW_BAND_NAME = "cloud_ice_snow";
    public static final String WV_BAND_NAME = "water_vapour";
    public static final String NDVI_BAND_NAME = "ndvi";
    public static final String NDSI_BAND_NAME = "ndsi";
    public static final String MDSI_BAND_NAME = "mdsi";

    public static final int FLAG_UNCERTAIN = 0;
    public static final int FLAG_ICE = 1;
    public static final int FLAG_SNOW = 2;
    public static final int FLAG_CLOUD = 4;

    private LookupTable[][] rtmLookupTables;

    private static String productName = "SNOWRADIANCE PRODUCT";
    private static String productType = "SNOWRADIANCE PRODUCT";

    private double[][][] tsfcLut;
    private double[] tLowestLayer = new double[SnowRadianceConstants.NUMBER_ATMOSPHERIC_PROFILES];

    private static float NDSI_THRESH_CLOUD_LOWER = 0.2f;
    private static float NDSI_THRESH_SNOW_LOWER = 0.6f;
    private static float NDSI_THRESH_SNOW_UPPER = 0.85f;

    private SnowGrainSizePollutionRetrieval snowGrainSizePollutionRetrieval;
    private Band[] merisReflectanceBands;


    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public SnowPropertiesOp() {
    }

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link org.esa.beam.framework.datamodel.Product} annotated with the
     * {@link org.esa.beam.framework.gpf.annotations.TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        if (applyCloudMask) {
            if (getCloudMaskFromSynergy) {
//                Map<String, Product> cloudScreeningInput = new HashMap<String, Product>(1);
//                cloudScreeningInput.put("source", colocatedProduct);
//                Map<String, Object> cloudScreeningParams = new HashMap<String, Object>(4);
//                cloudScreeningParams.put("useForwardView", true);
//                cloudScreeningParams.put("computeCOT", true);
//                cloudScreeningParams.put("computeSF", true);
//                cloudScreeningParams.put("computeSH", true);
//                cloudScreeningProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(SynergyCloudScreeningOp.class), cloudScreeningParams, cloudScreeningInput);
            } else {
                Map<String, Product> cloudProbabilityInput = new HashMap<String, Product>(1);
                colocatedProduct.setProductType("MER_RR__1P");
                cloudProbabilityInput.put("input", merisProduct);
                Map<String, Object> cloudProbabilityParameters = new HashMap<String, Object>(3);
                cloudProbabilityParameters.put("configFile", "cloud_config.txt");
                cloudProbabilityParameters.put("validLandExpression", "not l1_flags" + ".INVALID and dem_alt > -50");
                cloudProbabilityParameters.put("validOceanExpression", "not l1_flags" + ".INVALID and dem_alt <= -50");
                cloudProbabilityProduct = GPF.createProduct("Meris.CloudProbability", cloudProbabilityParameters, cloudProbabilityInput);
            }
        }

        createTargetProduct();

        ProductUtils.copyTiePointGrids(colocatedProduct, targetProduct);
        ProductUtils.copyGeoCoding(colocatedProduct, targetProduct);
        ProductUtils.copyMetadata(colocatedProduct, targetProduct);

        try {
            rtmLookupTables = SnowRadianceAuxData.createRtmLookupTables();
            tsfcLut = SnowRadianceAuxData.getTsfcFromLookupTables();
            for (int i = 0; i < SnowRadianceConstants.NUMBER_ATMOSPHERIC_PROFILES; i++) {
                tLowestLayer[i] = tsfcLut[i][0][24];
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to read RTM lookup tables:\n" + e.getMessage(), e);
        }

        // snow grain size / pollution retrieval...
        Map<String, Object> emptyParams = new HashMap<String, Object>();
        Product rad2reflProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), emptyParams, merisProduct);

        merisReflectanceBands = new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflectanceBands[i] = rad2reflProduct.getBand("rho_toa_" + (i + 1));
        }

        snowGrainSizePollutionRetrieval = new SnowGrainSizePollutionRetrieval();

    }


    private void createTargetProduct() {
        targetProduct = new Product(productName,
                                    productType,
                                    colocatedProduct.getSceneRasterWidth(),
                                    colocatedProduct.getSceneRasterHeight());

        createTargetProductBands();
    }

    private void createTargetProductBands() {

        if (copyInputBands) {
            for (Band band : colocatedProduct.getBands()) {
                if (!band.isFlagBand()) {
                    ProductUtils.copyBand(band.getName(), colocatedProduct, targetProduct);
                }
            }
        }

        // temperature / emissivity bands:
        if (computeSnowTemperatureFub) {
            Band snowTempBand = targetProduct.addBand(SnowRadianceConstants.SNOW_TEMPERATURE_BAND_NAME, ProductData.TYPE_FLOAT32);
            snowTempBand.setNoDataValue(SnowRadianceConstants.SNOW_TEMPERATURE_BAND_NODATAVALUE);
            snowTempBand.setNoDataValueUsed(SnowRadianceConstants.SNOW_TEMPERATURE_BAND_NODATAVALUE_USED);
            snowTempBand.setUnit("K");
        }

        if (computeEmissivityFub) {
            Band emissivityBand = targetProduct.addBand(SnowRadianceConstants.EMISSIVITY_BAND_NAME, ProductData.TYPE_FLOAT32);
            emissivityBand.setNoDataValue(SnowRadianceConstants.EMISSIVITY_BAND_NODATAVALUE);
            emissivityBand.setNoDataValueUsed(SnowRadianceConstants.EMISSIVITY_BAND_NODATAVALUE_USED);
            emissivityBand.setUnit("K");
        }

        Band cloudIceSnowBand = targetProduct.addBand(CLOUDICESNOW_BAND_NAME, ProductData.TYPE_INT16);
        cloudIceSnowBand.setDescription("Cloud/Ice/Snow flags");
        cloudIceSnowBand.setNoDataValue(-1);
        cloudIceSnowBand.setNoDataValueUsed(true);

        FlagCoding flagCoding = createCloudIceSnowFlagCoding(targetProduct);
        cloudIceSnowBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        // snow grain size / pollution bands...:
        if (computeSnowGrainSize) {
            Band unpollutedSnowGrainSizeBand = targetProduct.addBand(SnowRadianceConstants.UNPOLLUTED_SNOW_GRAIN_SIZE_BAND_NAME, ProductData.TYPE_FLOAT32);
            unpollutedSnowGrainSizeBand.setNoDataValue(SnowRadianceConstants.UNPOLLUTED_SNOW_GRAIN_SIZE_BAND_NODATAVALUE);
            unpollutedSnowGrainSizeBand.setNoDataValueUsed(SnowRadianceConstants.UNPOLLUTED_SNOW_GRAIN_SIZE_BAND_NODATAVALUE_USED);
            unpollutedSnowGrainSizeBand.setUnit("mm");
        }

        if (computeSnowSootContent) {
            Band pollutedSnowGrainSizeBand = targetProduct.addBand(SnowRadianceConstants.SOOT_CONCENTRATION_BAND_NAME, ProductData.TYPE_FLOAT32);
            pollutedSnowGrainSizeBand.setNoDataValue(SnowRadianceConstants.SOOT_CONCENTRATION_BAND_NODATAVALUE);
            pollutedSnowGrainSizeBand.setNoDataValueUsed(SnowRadianceConstants.SOOT_CONCENTRATION_BAND_NODATAVALUE_USED);
            pollutedSnowGrainSizeBand.setUnit("ng/g");
        }

        if (computeSnowAlbedo) {
            Band[] snowAlbedoBand = new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                snowAlbedoBand[i] = targetProduct.addBand(SnowRadianceConstants.SNOW_ALBEDO_BAND_NAME + "_" + i, ProductData.TYPE_FLOAT32);
                snowAlbedoBand[i].setNoDataValue(SnowRadianceConstants.SNOW_ALBEDO_BAND_NODATAVALUE);
                snowAlbedoBand[i].setNoDataValueUsed(SnowRadianceConstants.SNOW_ALBEDO_BAND_NODATAVALUE_USED);
                snowAlbedoBand[i].setUnit("dl");
            }
        }

        // complementary quantities
        if (computeMerisWaterVapour) {
            Band wvBand = targetProduct.addBand(WV_BAND_NAME, ProductData.TYPE_FLOAT32);
            wvBand.setDescription("NDSI");
            wvBand.setNoDataValue(-1.0f);
            wvBand.setNoDataValueUsed(true);
        }

        if (computeMerisNdvi) {
            Band ndviBand = targetProduct.addBand(NDVI_BAND_NAME, ProductData.TYPE_FLOAT32);
            ndviBand.setDescription("NDVI");
            ndviBand.setNoDataValue(-1.0f);
            ndviBand.setNoDataValueUsed(true);
        }

        if (computeAatsrNdsi) {
            Band ndsiBand = targetProduct.addBand(NDSI_BAND_NAME, ProductData.TYPE_FLOAT32);
            ndsiBand.setDescription("NDSI");
            ndsiBand.setNoDataValue(-1.0f);
            ndsiBand.setNoDataValueUsed(true);
        }

        if (computeMerisMdsi) {
            Band mdsiBand = targetProduct.addBand(MDSI_BAND_NAME, ProductData.TYPE_FLOAT32);
            mdsiBand.setDescription("MDSI");
            mdsiBand.setNoDataValue(-1.0f);
            mdsiBand.setNoDataValueUsed(true);
        }

        if (copyAatsrL1Flags) {
            ProductUtils.copyFlagBands(colocatedProduct, targetProduct);
            System.out.println("");
        }
    }


    public static FlagCoding createCloudIceSnowFlagCoding(Product outputProduct) {
        MetadataAttribute cloudAttr;
        final FlagCoding flagCoding = new FlagCoding(CLOUDICESNOW_BAND_NAME);
        flagCoding.setDescription("Cloud Flag Coding");

        cloudAttr = new MetadataAttribute("cloudcovered", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_CLOUD);
        cloudAttr.setDescription("is with more than 80% cloudy");
        flagCoding.addAttribute(cloudAttr);
        outputProduct.addBitmaskDef(new BitmaskDef(cloudAttr.getName(),
                                                   cloudAttr.getDescription(),
                                                   flagCoding.getName() + "." + cloudAttr.getName(),
                                                   createBitmaskColor(1, 3),
                                                   0.5F));

        cloudAttr = new MetadataAttribute("icecovered", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_ICE);
        cloudAttr.setDescription("is covered with ice (NDSI criterion)");
        flagCoding.addAttribute(cloudAttr);
        outputProduct.addBitmaskDef(new BitmaskDef(cloudAttr.getName(),
                                                   cloudAttr.getDescription(),
                                                   flagCoding.getName() + "." + cloudAttr.getName(),
                                                   createBitmaskColor(2, 3),
                                                   0.5F));

        cloudAttr = new MetadataAttribute("snowcovered", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_SNOW);
        cloudAttr.setDescription("is covered with snow (AATSR band criterion)");
        flagCoding.addAttribute(cloudAttr);
        outputProduct.addBitmaskDef(new BitmaskDef(cloudAttr.getName(),
                                                   cloudAttr.getDescription(),
                                                   flagCoding.getName() + "." + cloudAttr.getName(),
                                                   createBitmaskColor(3, 3),
                                                   0.5F));

        return flagCoding;
    }

    /**
     * Creates a new color object to be used in the bitmaskDef.
     * The given indices start with 1.
     *
     * @param index
     * @param maxIndex
     * @return the color
     */
    private static Color createBitmaskColor(int index, int maxIndex) {
        final double rf1 = 0.0;
        final double gf1 = 0.5;
        final double bf1 = 1.0;

        final double a = 2 * Math.PI * index / maxIndex;

        return new Color((float) (0.5 + 0.5 * Math.sin(a + rf1 * Math.PI)),
                         (float) (0.5 + 0.5 * Math.sin(a + gf1 * Math.PI)),
                         (float) (0.5 + 0.5 * Math.sin(a + bf1 * Math.PI)));
    }


    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetBand The target band.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        JnnNet neuralNetWv;
        try {
            neuralNetWv = SnowRadianceAuxData.getInstance().loadNeuralNet(SnowRadianceAuxData.NEURAL_NET_WV_OCEAN_MERIS_FILE_NAME);
        } catch (IOException e) {
            throw new OperatorException("Failed to read WV neural net:\n" + e.getMessage(), e);
        } catch (JnnException e) {
            throw new OperatorException("Failed to load WV neural net:\n" + e.getMessage(), e);
        }

        Rectangle rectangle = targetTile.getRectangle();

        Tile zonalWindTile = getSourceTile(colocatedProduct.getTiePointGrid("zonal_wind"), rectangle, pm);
        Tile meridWindTile = getSourceTile(colocatedProduct.getTiePointGrid("merid_wind"), rectangle, pm);
        Tile saMerisTile = getSourceTile(colocatedProduct.getTiePointGrid("sun_azimuth"), rectangle, pm);
        Tile szMerisTile = getSourceTile(colocatedProduct.getTiePointGrid("sun_zenith"), rectangle, pm);
        Tile vaMerisTile = getSourceTile(colocatedProduct.getTiePointGrid("view_azimuth"), rectangle, pm);
        Tile vzMerisTile = getSourceTile(colocatedProduct.getTiePointGrid("view_zenith"), rectangle, pm);

        Tile merisRad14Tile = getSourceTile(colocatedProduct.getBand("radiance_14" + "_M" + ""), rectangle, pm);
        Tile merisRad15Tile = getSourceTile(colocatedProduct.getBand("radiance_15" + "_M" + ""), rectangle, pm);

        Tile aatsrBTNadir1100Tile = getSourceTile(colocatedProduct.getBand("btemp_nadir_1100" + "_S" + ""), rectangle, pm);
        Tile aatsrBTNadir1200Tile = getSourceTile(colocatedProduct.getBand("btemp_nadir_1200" + "_S" + ""), rectangle, pm);

        Tile veAatsrNadirTile = getSourceTile(colocatedProduct.getBand("view_elev_nadir" + "_S" + ""), rectangle, pm);

        Tile aatsrReflecNadir550Tile = getSourceTile(colocatedProduct.getBand("reflec_nadir_0550" + "_S" + ""), rectangle, pm);
        Tile aatsrReflecNadir670Tile = getSourceTile(colocatedProduct.getBand("reflec_nadir_0670" + "_S" + ""), rectangle, pm);
        Tile aatsrReflecNadir870Tile = getSourceTile(colocatedProduct.getBand("reflec_nadir_0870" + "_S" + ""), rectangle, pm);
        Tile aatsrReflecNadir1600Tile = getSourceTile(colocatedProduct.getBand("reflec_nadir_1600" + "_S" + ""), rectangle, pm);

        Tile[] merisSpectralBandTiles = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisSpectralBandTiles[i] = getSourceTile(merisReflectanceBands[i], rectangle, pm);
        }

        Tile merisRefl2Tile = merisSpectralBandTiles[1];
        Tile merisRefl12Tile = merisSpectralBandTiles[11];
        Tile merisRefl13Tile = merisSpectralBandTiles[12];
        Tile merisRefl14Tile = merisSpectralBandTiles[13];

        Tile cloudFlagsTile = null;
        Tile cloudProbTile = null;
        if (applyCloudMask && !getCloudMaskFromSynergy) {
            if (getCloudMaskFromSynergy) {
//                cloudFlagsTile = getSourceTile(cloudScreeningProduct.getBand(SynergyConstants.B_CLOUDFLAGS), rectangle, pm);
            } else {
                cloudFlagsTile = getSourceTile(cloudProbabilityProduct.getBand(CloudProbabilityOp.CLOUD_FLAG_BAND), rectangle, pm);
                cloudProbTile = getSourceTile(cloudProbabilityProduct.getBand(CloudProbabilityOp.CLOUD_PROP_BAND), rectangle, pm);
            }
        }


        int x0 = rectangle.x;
        int y0 = rectangle.y;
        int w = rectangle.width;
        int h = rectangle.height;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {

                if (pm.isCanceled()) {
                    break;
                }

                if (targetBand.isFlagBand() && !targetBand.getName().equals(CLOUDICESNOW_BAND_NAME)) {
                    Tile flagTile = getSourceTile(colocatedProduct.getBand(targetBand.getName()), rectangle, pm);
                    targetTile.setSample(x, y, flagTile.getSampleInt(x, y));
                } else {

                    // first determine cloud mask...
                    boolean considerPixelAsCloudy = applyCloudMask && isCloud(cloudFlagsTile, cloudProbTile, x, y);

                    if (!considerPixelAsCloudy) {
                        if (doSnowTemperatureEmissivityRetrieval()) {
                            final float aatsrBt11 = aatsrBTNadir1100Tile.getSampleFloat(x, y);
                            final float aatsrBt12 = aatsrBTNadir1200Tile.getSampleFloat(x, y);

                            // temperature/emissivity retrieval...
                            if (aatsrBt11 > 0.0 && aatsrBt12 > 0.0 && !(aatsrBt11 == Float.NaN) && !(aatsrBt12 == Float.NaN)) {

                                if (targetBand.getName().equals(CLOUDICESNOW_BAND_NAME)) {
                                    // cloud, ice, snow retrieval using NDSI thresholds
                                    targetTile.setSample(x, y, FLAG_UNCERTAIN);
                                    float aatsr865 = aatsrReflecNadir870Tile.getSampleFloat(x, y);
                                    float aatsr1610 = aatsrReflecNadir1600Tile.getSampleFloat(x, y);
                                    float ndsi = (aatsr865 - aatsr1610) / (aatsr865 + aatsr1610);
                                    if (ndsi > ndsiLowerThreshold && ndsi < ndsiUpperThreshold) {
                                        targetTile.setSample(x, y, FLAG_SNOW);
                                    } else if (ndsi > ndsiUpperThreshold) {
                                        targetTile.setSample(x, y, FLAG_ICE);
                                    }
                                    if (apply100PercentSnowMask && !(ndsi > ndsiUpperThreshold)) {
                                        boolean is1600InInterval = aatsr1610 >= aatsr1610LowerThreshold && aatsr1610 <= aatsr1610UpperThreshold;
                                        boolean isSnow = is1600InInterval;
                                        if (isSnow) {
                                            targetTile.setSample(x, y, FLAG_SNOW);
                                        } else {
                                            targetTile.setSample(x, y, FLAG_UNCERTAIN);
                                        }
                                    }
                                } else {
                                    // 3.2.3 Calculation of water vapour

//                        float waterVapourColumn = SnowTemperatureEmissivityRetrieval.computeWaterVapour(neuralNetWv, zonalWind, meridWind, merisAzimuthDifference,
//                                                                                              merisViewZenith, merisSunZenith, merisRad14, merisRad15);
                                    float waterVapourColumn = 0.3f; // simplification, might be sufficient (RP, 2010/04/14)

                                    // 3.2.4 temperature retrieval

                                    final float aatsrViewElevationNadir = veAatsrNadirTile.getSampleFloat(x, y);
                                    final float viewZenith = 90.0f - aatsrViewElevationNadir;

                                    float tempSurface = SnowTemperatureEmissivityRetrieval.
                                            minimizeNewtonForTemperature(assumedEmissivityAt11Microns, waterVapourColumn, viewZenith, aatsrBt11, rtmLookupTables, tLowestLayer);

                                    if (computeSnowTemperatureFub && targetBand.getName().equals(SnowRadianceConstants.SNOW_TEMPERATURE_BAND_NAME)) {
                                        targetTile.setSample(x, y, tempSurface);
                                    }
                                    if (computeEmissivityFub && targetBand.getName().equals(SnowRadianceConstants.EMISSIVITY_BAND_NAME)) {

                                        float emissivity = SnowTemperatureEmissivityRetrieval.
                                                minimizeNewtonForEmissivity(waterVapourColumn, viewZenith, tempSurface, aatsrBt12,
                                                                            rtmLookupTables, tLowestLayer);
                                        targetTile.setSample(x, y, emissivity);
                                    }
                                }
                            } else {
                                if (computeSnowTemperatureFub && targetBand.getName().equals(SnowRadianceConstants.SNOW_TEMPERATURE_BAND_NAME)) {
                                    targetTile.setSample(x, y, SnowRadianceConstants.SNOW_TEMPERATURE_BAND_NODATAVALUE);
                                }
                                if (computeEmissivityFub && targetBand.getName().equals(SnowRadianceConstants.EMISSIVITY_BAND_NAME)) {
                                    targetTile.setSample(x, y, SnowRadianceConstants.EMISSIVITY_BAND_NODATAVALUE);
                                }
                                if (targetBand.getName().equals(CLOUDICESNOW_BAND_NAME)) {
                                    targetTile.setSample(x, y, FLAG_UNCERTAIN);
                                }
                            }
                        }
                    } else {
                        if (targetBand.getName().equals(CLOUDICESNOW_BAND_NAME)) {
                            targetTile.setSample(x, y, FLAG_CLOUD);
                        }
                    }

                    // snow grain size / pollution retrieval...
                    if (doSnowGrainSizePollutionRetrieval()) {
                        double saa = saMerisTile.getSampleDouble(x, y);
                        double sza = szMerisTile.getSampleDouble(x, y);
                        double vaa = vaMerisTile.getSampleDouble(x, y);
                        double vza = vzMerisTile.getSampleDouble(x, y);
                        double reflFunction;

                        reflFunction = snowGrainSizePollutionRetrieval.computeReflLutApprox(saa, sza, vaa, vza);

                        double merisRefl2 = merisRefl2Tile.getSampleDouble(x, y);
                        double merisRefl13 = merisRefl13Tile.getSampleDouble(x, y);

                        if (computeSnowGrainSize && targetBand.getName().equals(SnowRadianceConstants.UNPOLLUTED_SNOW_GRAIN_SIZE_BAND_NAME)) {
                            double unpollutedSnowGrainSize =
                                    snowGrainSizePollutionRetrieval.getParticleAbsorptionLength(merisRefl2, merisRefl13, reflFunction, sza, vza);
                            targetTile.setSample(x, y, unpollutedSnowGrainSize);
                        }

                        if (computeSnowSootContent && targetBand.getName().equals(SnowRadianceConstants.SOOT_CONCENTRATION_BAND_NAME)) {
                            double pal =
                                    snowGrainSizePollutionRetrieval.getParticleAbsorptionLength(merisRefl2, merisRefl13, reflFunction, sza, vza);
                            double unpollutedSnowGrainSize =
                                    snowGrainSizePollutionRetrieval.getUnpollutedSnowGrainSize(pal);
                            double sootConcentration =
                                    snowGrainSizePollutionRetrieval.getSootConcentrationInPollutedSnow(merisRefl13, reflFunction, sza, vza,
                                                                                                       unpollutedSnowGrainSize);
                            targetTile.setSample(x, y, sootConcentration);
                        }

                        if (computeSnowAlbedo && targetBand.getName().startsWith(SnowRadianceConstants.SNOW_ALBEDO_BAND_NAME)) {
                            int snowAlbedoBandPrefixLength = SnowRadianceConstants.SNOW_ALBEDO_BAND_NAME.length();
                            String snowAlbedoBandIndexString = targetBand.getName().
                                    substring(snowAlbedoBandPrefixLength + 1, targetBand.getName().length());
                            int snowAlbedoBandIndex = Integer.parseInt(snowAlbedoBandIndexString);
                            double merisRefl = merisSpectralBandTiles[snowAlbedoBandIndex].getSampleDouble(x, y);
                            double snowAlbedo =
                                    snowGrainSizePollutionRetrieval.getSnowAlbedo(merisRefl, reflFunction, sza, vza);
                            targetTile.setSample(x, y, snowAlbedo);
                        }
                    }

                    // complementary quantities...
                    float merisViewAzimuth = vaMerisTile.getSampleFloat(x, y);
                    float merisSunAzimuth = saMerisTile.getSampleFloat(x, y);
                    final float zonalWind = zonalWindTile.getSampleFloat(x, y);
                    final float meridWind = meridWindTile.getSampleFloat(x, y);
                    float merisAzimuthDifference = SnowTemperatureEmissivityRetrieval.removeAzimuthDifferenceAmbiguity(merisViewAzimuth,
                                                                                                                       merisSunAzimuth);
                    final float merisViewZenith = vzMerisTile.getSampleFloat(x, y);
                    final float merisSunZenith = szMerisTile.getSampleFloat(x, y);
                    final float merisRad14 = merisRad14Tile.getSampleFloat(x, y);
                    final float merisRad15 = merisRad15Tile.getSampleFloat(x, y);

                    if (computeMerisWaterVapour && targetBand.getName().equals(WV_BAND_NAME)) {
                        final float merisWaterVapourColumn = SnowTemperatureEmissivityRetrieval.computeWaterVapour(neuralNetWv, zonalWind, meridWind, merisAzimuthDifference,
                                                                                                                   merisViewZenith, merisSunZenith, merisRad14, merisRad15);
                        targetTile.setSample(x, y, merisWaterVapourColumn);
                    }

                    if (computeMerisNdvi && targetBand.getName().equals(NDVI_BAND_NAME)) {
                        final float merisRefl12 = merisRefl12Tile.getSampleFloat(x, y);
                        final float merisRefl13 = merisRefl13Tile.getSampleFloat(x, y);
                        final double ndvi = (merisRefl12 - merisRefl13) / (merisRefl12 + merisRefl13);
                        targetTile.setSample(x, y, ndvi);
                    }

                    if (computeAatsrNdsi && targetBand.getName().equals(NDSI_BAND_NAME)) {
                        final float aatsr865 = aatsrReflecNadir870Tile.getSampleFloat(x, y);
                        final float aatsr1610 = aatsrReflecNadir1600Tile.getSampleFloat(x, y);
                        final float ndsi = (aatsr865 - aatsr1610) / (aatsr865 + aatsr1610);
                        targetTile.setSample(x, y, ndsi);
                    }

                    if (computeMerisMdsi && targetBand.getName().equals(MDSI_BAND_NAME)) {
                        final float merisRefl13 = merisRefl13Tile.getSampleFloat(x, y);
                        final float merisRefl14 = merisRefl14Tile.getSampleFloat(x, y);
                        final double mdsi = (merisRefl13 - merisRefl14) / (merisRefl13 + merisRefl14);
                        targetTile.setSample(x, y, mdsi);
                    }
                }
            }
        }
    }

    private boolean isCloud(Tile cloudFlagsTile, Tile cloudProbTile, int x, int y) {
        boolean isCloud = false;
        if (!applyCloudMask) {
            return false;
        }

        if (getCloudMaskFromSynergy) {
//            isCloud = cloudFlagsTile.getSampleBit(x, y, SynergyConstants.FLAGMASK_CLOUD);
        } else {
            float cloudProb = cloudProbTile.getSampleFloat(x, y);
            isCloud = (cloudProb > cloudProbabilityThreshold);
        }

        return isCloud;
    }

    private boolean doSnowTemperatureEmissivityRetrieval() {
        return (computeSnowTemperatureFub || computeEmissivityFub);
    }

    private boolean doSnowGrainSizePollutionRetrieval() {
        return (computeSnowGrainSize || computeSnowSootContent || computeSnowAlbedo);
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(SnowPropertiesOp.class);
        }
    }
}
