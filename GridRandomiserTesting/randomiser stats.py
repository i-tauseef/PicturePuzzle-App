""" Analyse randomly generated lists of consecutive integers corresponding to n x n grids """

import pandas as pd
import matplotlib.pyplot as plt
# import seaborn as sns
# import numpy as np
from math import factorial as fct

table_min_freq = []
table_max_freq = []
table_med_freq = []


def analyse_table_data(data_file):
    # open the file containing the data and add each value from each line to the corresponding list
    # the lists are used to construct a dataframe which is used to determine some statistics and display plots
    grids = []
    frequencies = []
    num_grids = 0
    with open(data_file, 'r') as grid_dataset:
        # take the grid string and frequency and append each into their corresponding list to be used for analysis
        for line in grid_dataset:
            # get indexes of the characters separating the grid string and frequency string and slice each line
            string_end_index = line.find(" ")
            newline_index = line.find("\\")
            grid_string = line[:string_end_index]  # get the grid
            print(grid_string)
            frequency = line[string_end_index + 1:newline_index]  # get the frequency
            print(frequency)
            num_grids += int(frequency)  # count number of grids in dataset using frequency values
            # add values to lists
            grids.append(grid_string)
            frequencies.append(int(frequency))
            print(num_grids)
    grid_dataset.close()  # think with closes the file but kept this anyway...

    # display data in scatter plots and histogram
    df = pd.DataFrame({"grid": grids, "frequency": frequencies})
    freq_sorted_series = df['frequency'].sort_values()
    print(df.describe())
    # plt.scatter(df.index, df['frequency'], 0.5)
    # plt.show()
    plt.hist(freq_sorted_series, bins=19)
    plt.show()


analyse_table_data("randomiser datasets/gridsize(9)_numgrids(10000000)_tableform.txt")


def analyse_dataset(dataset_file, grid_size):
    # open the file containing the data and create array with results
    unique_grids = dict()
    counter = 0
    with open(dataset_file, 'r') as grid_dataset:
        # Use a dictionary to accumulate unique grids and their frequencies to be used for data analysis. Dict is a
        # good option as searching for unique grids is fast. Each grid, which is a in the dataset (txt file), is used
        # as a key for the dictionary. If there is no value associated with it yet, then the value is set to 1. If
        # there is, then increment the value by 1. Thus, every unique grid from the dataset is a key in the dictionary,
        # and the associated value is the frequency of that grid being generated.
        for line in grid_dataset:
            # if no key in dict for that grid, add it and set value to 1 for the frequency
            if unique_grids.get(line) is None:
                unique_grids[line] = 1
            else:  # if key is already present, increase value by 1
                unique_grids[line] += 1
            counter += 1
    grid_dataset.close()  # think with closes the file but kept this anyway...

    # grid_list = [grid for grid in unique_grids]
    # grid_indexes = [index for index in range(len(unique_grids))]
    # frequency_list = [unique_grids[key] for key in unique_grids]

    # make a dataframe (used to display data in plots) with two columns :
    # "grid", gives a unique grid as a string of the numbers that make up the grid
    # "frequency", gives the frequency of the unique grid in the dictionary (how many were generated)
    df = pd.DataFrame({"grid": [grid for grid in unique_grids],
                       "frequency": [unique_grids[key] for key in unique_grids]})
    # TODO | NOTE: DataFrame has method from_dict to convert, but essentially performs the above operations to do so
    # df = pd.DataFrame.from_dict(data=unique_grids, orient='index')  # keeps names of grid, slow to load scatter
    # df.columns = ['frequency']

    # print out some stats regarding the dataset: number of possible unique (solvable) grids, number of unique grids
    # generated, mean/median/std dev/min/max for frequency of unique grids
    num_solvable_combinations = int(fct(grid_size)/2)  # factorials always even...use int
    num_unique_grids = len(unique_grids)
    percent_grids_created = num_unique_grids/num_solvable_combinations*100
    percent_unique = num_unique_grids/counter
    print("\nThere are {:d} possible (solvable) combinations \nof {:d} consecutive"
          " integers in a grid.".format(num_solvable_combinations, grid_size))
    print("{:.3f}% ({:d}) of possible unique grids were \nfound from a total of {:d}" 
          " generated.\n".format(percent_grids_created, num_unique_grids, counter))
    print(df.describe())

    # Sorted unique grid frequency values in ascending order and displayed the data in a scatter plot and histogram.
    # These clearly display how many of each unique grid was generated by the randomiser, which can show whether or not
    # the grids are generated in an appropriately random manner. If every possible unique grid is created, with
    # relatively small spread of frequencies (not favouring any particular grid), then it is sufficiently random.

    # freq_sorted_series = df['frequency'].sort_values()
    # plt.scatter(df.index, df['frequency'], 0.5)
    # # plt.scatter(df.index, freq_sorted_series, 1)
    # plt.title("Scatter plot for frequency of unique lists, in a dataset of\n "
    #           + str(counter) + " randomly generated lists of values 1 through " + str(grid_size))
    # plt.xlabel("Index of list in dataset")
    # plt.ylabel("Frequency of unique lists")
    # plt.show()

    # plt.hist(freq_sorted_series, bins=30)
    # plt.title("Histogram to display the spread of different frequencies of\n unique lists from a dataset"
    #           " of " + str(counter) + " generated lists")
    # plt.xlabel("Unique frequency values")
    # plt.ylabel("Frequency of frequency unique lists")
    # plt.grid = True
    # plt.show()

    # Determined the unique grids with the maximum, minimum, and median frequency in the 10mil dataset (largest)
    # Created multiple datasets of same size (1mil x 10), then determined the frequency of each of the aforementioned
    # grids in each dataset, to calculate the average frequency and check it is still approx. 49.6 (1mil/20.1k)

    # Get the min, max, and median frequency values, and determine the grids these correspond to
    # min_freq_grid = df.loc[df['frequency'] == 402]
    # max_freq_grid = df.loc[df['frequency'] == 585]
    # med_freq_grid = df.loc[df['frequency'] == 496]
    # print("\nlow:\n" + str(min_freq_grid) + "\nhigh:\n" + str(max_freq_grid) +
    #       "\nmedian:\n" + str(med_freq_grid))

    # For subsequent datasets, slice dataframe to get the data for each of the unique grids chosen (min,max,med) to
    # track the frequency across different datasets. Can then assess if there is any preference for specific grids
    # even if the distribution looks random
    min_freq_df = df.loc[df['grid'] == "0,6,4,3,5,1,2,7,8,\n"]
    max_freq_df = df.loc[df['grid'] == "7,4,5,6,2,3,1,0,8,\n"]
    med_freq_df = df.loc[df['grid'] == "4,5,3,0,2,6,7,1,8,\n"]
    # print("\nmin:\n" + str(min_freq_df) + "\nmax:\n" + str(max_freq_df)
    #       + "\nmedian:\n" + str(med_freq_df))
    min_freq_val = min_freq_df.iloc[0]['frequency']
    max_freq_val = max_freq_df.iloc[0]['frequency']
    med_freq_val = med_freq_df.iloc[0]['frequency']
    # Add frequency values to a lists to create a table for the data
    table_min_freq.append(min_freq_val)
    table_max_freq.append(max_freq_val)
    table_med_freq.append(med_freq_val)

    # TODO: can test how a change in function might affect randomness for instance, can swap a random cell with random
    # neighbour or any other cell then check if it has changed inversions. I think swapping wih 1 beside changes
    # inversions by set amount? think of an unsolvable puzzle in closest to solved
    # position, there will be 13, 15, 14, _ in last row, meaning 1 is out of place and only requires one swap

    # Testing Chi Squared goodness of fit statistic for frequency of unique grids:
    # X^2 = sum((observed-expected)^2 / expected)
    chi_squared = 0
    expected_frequency = counter/num_solvable_combinations  # num grids generated/num possible unique grids
    print(expected_frequency)
    for grid in unique_grids:
        chi_squared += ((unique_grids[grid] - expected_frequency)**2)/expected_frequency
    print("\nchi squared: " + str(chi_squared))
    # Is very large number...extends to infintity as dataset is larger


# analyse_dataset("randomiser datasets/gridsize(9)_numgrids(10000000).txt", 8)
# for x in range(10):
#     analyse_dataset("randomiser datasets/gridsize(9)_numgrids(1000000)_copy("+str(x)+").txt", 8)
# data_table = pd.DataFrame(data={"min grid": table_min_freq,
#                                 "max grid": table_max_freq, "med grid": table_med_freq})
# print()
# print(data_table)
# print()
# print("max grid mean frequency: ", data_table['max grid'].mean())
# print("min grid mean frequency: ", data_table['min grid'].mean())
# print("med grid mean frequency: ", data_table['med grid'].mean())


def use_list():
    # TODO: Tested using list, but is extremely slow as I thought (hence why originally used dict) as we must search through
    # the array (worst case whole array) for every line in the dataset, which increases as we find unique grids
    unique_grids_array = []
    counter = 0
    with open('3grid10mil.txt', 'r') as grid_dataset:
        for grid in grid_dataset:
            try:
                grid_array_index = unique_grids_array.index(grid)
                unique_grids_array[grid_array_index][1] += 1
                print('test')
            except ValueError as e:
                print(e)
                unique_grids_array.append([grid, 1])
            counter += 1
            print(counter)
            print(unique_grids_array)
        print('test2')


# use_list()
