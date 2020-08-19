import React, { ReactNode, useEffect } from 'react'
import { css } from 'emotion'
import { ResponsiveContainer, ComposedChart, Area, Bar, XAxis, YAxis, CartesianGrid, LabelProps, RectangleProps, Line } from 'recharts'
import { Paper, Fab } from '@material-ui/core'
import { Close, Vanish, CarDefrostRear } from 'mdi-material-ui'
import { DateTime } from 'luxon'
import produce from 'immer'
import { padStart } from 'lodash'

import UpdateSprinklingDialog from './UpdateSprinklingDialog'
import DateView from './DateView'
import { ReactComponent as AlfalfaIcon } from './icons/alfalfa.svg'
import { ReactComponent as CornIcon } from './icons/corn.svg'
import { ReactComponent as GenericIcon } from './icons/generic.svg'
import { ReactComponent as PotatoIcon } from './icons/potato.svg'
import { ReactComponent as WheatIcon } from './icons/wheat.svg'
import { ReactComponent as RainfallIcon } from './icons/rainfall.svg'
import { ReactComponent as IrrigationIcon } from './icons/irrigation.svg'
import PlotListDialog from './PlotListDialog';

const getCropTypeIcon = (cropType: string) => {
  switch(cropType && cropType.trim()) {
    case 'Snijmais':
    case 'Mais CCM':
      return <CornIcon width={28} fill="#00acc1"/>
    case 'Cons. en industrieaardappelen.':
      return <PotatoIcon width={28} stroke="#00acc1"/>
    case 'Luzerne.':
      return <AlfalfaIcon width={28} fill="#00acc1"/>
    case 'Winter Tarwe':
      return <WheatIcon width={28} fill="#00acc1"/>
    default:
      return <GenericIcon width={28} fill="#00acc1"/>
  }
}

let updateSprinklingDialog: UpdateSprinklingDialog

const SelectedSumData = ({ circleContent, label, text }: { circleContent: ReactNode, label: string, text: string}) =>
  <div
    className={css`
      display: flex;
      align-items: center;
      text-transform: uppercase;
      margin-left: 40px;
    `}
  >
    <div
      className={css`
        border: 1px solid #D2EDED;
        border-radius: 50%;
        width: 40px;
        height: 40px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-right: 10px;
      `}
    >{circleContent}</div>
    <div
      className={css`
        display: flex;
        flex-direction: column;
      `}
    >
      <small className={css`color: #BCBCBC;`}>{label}</small>
      <strong>{text}</strong>
    </div>
  </div>

const LegendItem = ({ label, shape, color }: { label: string, shape: 'square' | 'circle', color: string }) =>
  <div
    className={css`
      display: flex;
      align-items: center;
      margin-right: 20px;
    `}
  >
    {shape === 'square' &&
      <div
        className={css`
          width: 14px;
          height: 14px;
          margin-right: 5px;
          border-radius: 3px;
          background-color: ${color};
        `}
      />
    }
    {shape === 'circle' &&
      <div
        className={css`
          width: 10px;
          height: 10px;
          margin-right: 5px;
          border-radius: 50%;
          border: 2px solid ${color};
        `}
      />
    }
    <small className={css`color: #757575;`}>{label}</small>
  </div>

const CurrentDataItem = ({ label, value, color, icon }: { label: string, value: number, color: string, icon: any }) =>
  <div
    className={css`
      display: flex;
      align-items: center;
    `}
  >
    <div
      className={css`
        position: relative;
        border: 1px solid ${color};
        color: ${color};
        border-radius: 50%;
        width: 50px;
        height: 50px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-right: 10px;
      `}
    >
      <div className={css`position: relative; z-index: 1;`}>{Math.abs(value).toFixed()}</div>
      <div
        className={css`
          position: absolute;
          top: 0;
          left: 0;
          background-color: #fff;
          border-radius: 50%;
          padding: 0 2px 2px 0px;
        `}
      >{icon}</div>
    </div>
    <div
      className={css`
        color: ${color}
      `}
    >{label}</div>
  </div>

type Props = {
  navigate: (path: string) => void
  farmerData: any
  date: Date
  selectedPlotId?: string
  selectedPixel?: Array<number>
  sprinklingCache: any
  setSprinklingCache: (sprinklingCache: any) => void
}

export default ({ navigate, farmerData, date, selectedPlotId, selectedPixel, sprinklingCache, setSprinklingCache }: Props) => {

  useEffect(() => {
    window.dispatchEvent(new Event('resize'))
  }, [ selectedPlotId, selectedPixel ])

  const plots: GeoJSON.FeatureCollection = farmerData.plotsGeoJSON
  const { pixelsData, plotsAnalytics } = farmerData

  let label: string = ''
  let cropType: string = ''
  let soilType: string = ''
  let area: number = 0
  let data: Array<any> | undefined = undefined

  if(selectedPlotId) {
    label = `Plot ${selectedPlotId}`
    const feature = farmerData.plotsGeoJSON.features.find((f: any) => f.properties!.plotId === selectedPlotId)
    cropType = feature.properties.cropTypes
    soilType = feature.properties.soilType
    area = feature.properties.plotSizeHa
    data = plotsAnalytics[feature.properties.plotId].map((i: any, index: number) => ({
      date: DateTime.fromISO(i.date).toFormat('dd/MM/yyyy'),
      rainfall: i.measuredPrecipitation,
      sprinkling: sprinklingCache[`${selectedPlotId}-${index}`] || 0,
      moisture: i.availableSoilWater,
      desiredMoisture: i.desiredSoilWater,
      evapotranspiration: i.evapotranspiration,
      deficit: i.deficit
    }))
  }
  if(selectedPixel) {
    const [x, y] = selectedPixel
    label = `Pixel ${padStart(x.toString(), 3, '0')}${padStart(y.toString(), 3, '0')}`
    cropType = pixelsData.landUse[x][y]
    soilType = pixelsData.soilMap[x][y]
    area = 1
    data = pixelsData.analytics.map((i: any, index: number) => ({
      date: DateTime.fromISO(i.time).toFormat('dd/MM/yyyy'),
      rainfall: i.measuredPrecipitation[x][y],
      sprinkling: sprinklingCache[`${selectedPixel.join(',')}-${index}`] || 0,
      moisture: i.availableSoilWater[x][y],
      desiredMoisture: i.desiredSoilWater[x][y],
      evapotranspiration: i.evapotranspiration[x][y],
      deficit: i.deficit[x][y]
    }))
  }

  const current = data!.find(i => i.date === DateTime.fromJSDate(date).toFormat('dd/MM/yyyy'))
  const currentRainfall = current ? current.rainfall : 0
  const currentSprinkling = current ? current.sprinkling : 0
  const currentEvapotranspiration = current ? current.evapotranspiration : 0
  const currentDeficit = current ? current.deficit : 0

  return(
    <Paper
      elevation={5}
      className={css`
        position: relative;
        z-index: 1000;
      `}
      square
    >
      <Fab
        onClick={() => {
          navigate(`/map/${DateTime.fromJSDate(date).toISODate()}`)
          window.dispatchEvent(new Event('resize'))
        }}
        size="medium"
        className={css`
          position: absolute !important;
          right: 24px;
          top: -24px;
          background-color: #fff !important;
          color: #2F3D50 !important;
          box-shadow: 0px 3px 5px -1px #2F3D50, 0px -1px 10px 0px #2F3D50, 0px 1px 18px 0px #2F3D50 !important;
        `}
      >
        <Close/>
      </Fab>
      <div
        className={css`
          display: flex;
          align-items: flex-start;
          height: 60px;
          padding: 30px 20px 0 20px;
        `}
      >
        <div
          className={css`
            flex: 1;
            display: flex;
            flex-direction: column;
            color: #46958F;
          `}
        >
          <small
            className={css`
              font-weight: lighter;
              margin-left: 50px;
            `}
          >{label}</small>
          <DateView date={date}/>
        </div>
        <SelectedSumData
          circleContent={getCropTypeIcon(cropType)}
          label="Gewas"
          text={cropType}
        />
        <SelectedSumData
          circleContent={area}
          label="Hectare"
          text={soilType}
        />
        <PlotListDialog
          farmerData={farmerData}
          date={DateTime.fromJSDate(date).toISODate()}
          navigate={navigate}
          sprinklingCache={sprinklingCache}
        />
      </div>
      <div
        className={css`
          background-color: #FCFCFC;
          border-top: 1px solid #46958F;
        `}
      >
        <div
          className={css`
            display: flex;
            justify-content: space-between;
            padding: 10px 20px;
          `}
        >
          <CurrentDataItem
            label="Regenval in mm"
            value={currentRainfall}
            color="#64b5f6"
            icon={<RainfallIcon fill="#64b5f6" className={css`width: 20px; height: 20px;`}/>}
          />
          <CurrentDataItem
            label="Evapotranspiratie in mm"
            value={currentEvapotranspiration}
            color="#7cb342"
            icon={<CarDefrostRear fill="#7cb342" className={css`width: 18px !important; height: 18px !important; transform: rotate(180deg);`} />}
          />
          <CurrentDataItem
            label="Vochttekort in mm"
            value={currentDeficit}
            color="#ff5722"
            icon={<Vanish fill="#ff5722" width={20} className={css`width: 18px !important; height: 18px !important;`}/>}
          />
          <CurrentDataItem
            label="Te beregenen in mm"
            value={currentSprinkling}
            color="#1565c0"
            icon={<IrrigationIcon fill="#1565c0" className={css`width: 20px; height: 20px;`}/>}
          />
        </div>
        <div
          className={css`
            display: flex;
            padding-left: 20px;
          `}>
          <LegendItem label="Regenval in mm" shape="square" color="#64b5f6"/>
          <LegendItem label="Beregening in mm" shape="square" color="#1565c0"/>
          <LegendItem label="Vochtgehalte in mm" shape="circle" color="#fb8c00"/>
          <LegendItem label="Gewenst vochtgehalte in mm" shape="circle" color="#00acc1"/>
        </div>
        <ResponsiveContainer height={200}>
          <ComposedChart data={data} margin={{ top: 20, bottom: 0, left: 0, right: 0 }}>
            <defs>
              <linearGradient id="moistureColor" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#ff9800" stopOpacity={0.8}/>
                <stop offset="95%" stopColor="#ffe0b2" stopOpacity={0}/>
              </linearGradient>
              <radialGradient id="radial" fx="50%" fy="50%" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stopColor="#1e88e5" stopOpacity="1"/>
                <stop offset="100%" stopColor="#1e88e5" stopOpacity="0" />
              </radialGradient>
              <clipPath id="bar-rounded-corners">
                <rect x="0" y="0" width="100" height="100" rx="5" ry="5"/>
              </clipPath>
            </defs>
            <CartesianGrid/>
            <XAxis
              dataKey="date"
              xAxisId={0}
              axisLine={{ stroke: '#fb8c00' }}
              tickLine={false}
              tick={{ fill: '#757575', fontSize: 10 }}
            />
            <XAxis dataKey="date" xAxisId={1} hide/>
            <XAxis dataKey="date" xAxisId={2} hide/>
            <YAxis
              yAxisId="left"
              padding={{ bottom: 50, top: 0 }}
              axisLine={{ stroke: '#1e88e5' }}
              tickLine={false}
              tick={{ fill: '#1e88e5', fontSize: 10 }}
              width={30}
            />
            <YAxis
              yAxisId="right"
              orientation="right"
              axisLine={{ stroke: '#fb8c00' }}
              tickLine={false}
              tick={{ fill: '#fb8c00', fontSize: 10 }}
              width={30}
            />
            <Area
              dataKey="moisture"
              xAxisId={2}
              yAxisId="right"
              type="natural"
              stroke="#fb8c00"
              fill="url(#moistureColor)"
            />
            <Line
              dataKey="desiredMoisture"
              xAxisId={2}
              yAxisId="right"
              type="natural"
              stroke="#00acc1"
            />
            <Bar
              dataKey="rainfall"
              xAxisId={0}
              yAxisId="left"
              barSize={60}
              shape={({ x, y, width, height }: RectangleProps) =>
                height! < 10 ? null :
                <path
                  {...{ x, y, width, height }}
                  fill="#64b5f6"
                  opacity={0.8}
                  d={`m${x},${y! + height!} v-${height! - 10} a10,10 270 0 1 10 -10 h${width! - 20} a10,10 0 0 1 10 10 v${height! - 10} z`}
                />
              }
            />
            <Bar
              dataKey="sprinkling"
              xAxisId={1}
              yAxisId="left"
              fill="#1565c0"
              opacity={0.8}
              barSize={40}
              label={({ value, x, y, width, index }: LabelProps & { index: number }) =>
                <g
                  onClick={async () => {
                    const newValue = await updateSprinklingDialog.open(value as number)
                    const updatedCache = produce(sprinklingCache, sprinklingCache => {
                      sprinklingCache[`${selectedPlotId || selectedPixel!.join(',')}-${index}`] = newValue
                    })
                    setSprinklingCache(updatedCache)
                  }}
                >
                  <circle
                    cx={x! + width! / 2}
                    cy={y!}
                    r={width! / 2 + 8}
                    fill="url(#radial)"
                  />
                  <circle
                    cx={x! + width! / 2}
                    cy={y!}
                    r={width! / 2 + 3}
                    fill="#ffffff"
                    stroke="#64b5f6"
                  />
                  <circle
                    cx={x! + width! / 2}
                    cy={y!}
                    r={width! / 2}
                    fill="#ffffff"
                    stroke="#1565c0"
                    strokeWidth={2}
                  />
                  <text
                    x={x! + width! / 2}
                    y={y!}
                    textAnchor="middle"
                    dominantBaseline="middle"
                    fill="#1565c0"
                    fontSize={10}
                  >{value} mm</text>
                </g>
              }
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <UpdateSprinklingDialog ref={d => updateSprinklingDialog = d!}/>
    </Paper>
  )
}
